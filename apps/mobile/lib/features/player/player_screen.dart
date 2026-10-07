import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../../core/api/api_exception.dart';
import '../../core/config/app_config.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../core/utils/maturity.dart';
import '../../data/models/catalog_models.dart';
import '../../data/models/misc_models.dart';
import '../../data/repositories/playback_repositories.dart';
import 'player_args.dart';

enum _FailureKind { generic, device, notReady, blocked }

class PlayerScreen extends ConsumerStatefulWidget {
  const PlayerScreen({super.key, required this.args});

  final PlayerArgs args;

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> with WidgetsBindingObserver {
  static const _progressInterval = Duration(seconds: 15);
  static const _heartbeatInterval = Duration(seconds: 30);
  static const _autoplayCountdown = 10;

  VideoPlayerController? _video;
  PlaybackGrant? _grant;
  String? _status = 'Preparing playback…';
  String? _failure;
  _FailureKind _failureKind = _FailureKind.generic;
  bool _busyAction = false;

  Timer? _progressTimer;
  Timer? _heartbeatTimer;
  Timer? _hideTimer;
  Timer? _countdownTimer;
  int? _countdown;
  bool _controlsVisible = true;
  bool _wasPlaying = false;
  bool _ended = false;
  bool _closed = false;
  int _lastReportedSecond = -1;
  double? _dragValue;

  TvShowDetail? _show;
  Episode? _nextEpisode;

  // Captured up front: these are also used from dispose(), where `ref` is
  // no longer usable.
  late final PlaybackRepository _playback;
  late final WatchHistoryRepository _history;

  PlayerArgs get args => widget.args;

  @override
  void initState() {
    super.initState();
    _playback = ref.read(playbackRepositoryProvider);
    _history = ref.read(watchHistoryRepositoryProvider);
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    WakelockPlus.enable();
    _start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _shutdown();
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    WakelockPlus.disable();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.hidden) {
      _reportProgress(force: true);
      if (state == AppLifecycleState.paused) _video?.pause();
    }
  }

  // ---------------------------------------------------------------- startup

  Future<void> _start() async {
    setState(() {
      _failure = null;
      _status = 'Preparing playback…';
    });

    if (args.isTrailer) {
      await _openVideo(args.trailerUrl!, startAt: Duration.zero);
      return;
    }

    final profile = ref.read(currentProfileProvider);
    if (profile != null &&
        !isAllowedForProfile(
          contentRating: args.maturityRating,
          profileRating: profile.maturityRating,
          isKids: profile.isKids,
        )) {
      _fail("This title is above this profile's maturity rating.", _FailureKind.blocked);
      return;
    }

    var deviceId = ref.read(sessionControllerProvider).deviceId ??
        ref.read(sessionStoreProvider).deviceId;
    if (deviceId == null) {
      try {
        await ref.read(sessionControllerProvider.notifier).reRegisterDevice();
        deviceId = ref.read(sessionControllerProvider).deviceId;
      } on ApiException catch (e) {
        _fail(e.message, _FailureKind.generic);
        return;
      }
    }
    if (deviceId == null) {
      _fail('This device is not registered.', _FailureKind.device);
      return;
    }

    final PlaybackGrant grant;
    try {
      grant = await _playback.request(
            contentId: args.contentId,
            deviceId: deviceId,
            titleId: args.titleId,
          );
    } on ApiException catch (e) {
      if (!mounted) return;
      _handleRequestError(e);
      return;
    }
    if (!mounted || _closed) {
      _playback.stop(grant.sessionId).ignore();
      return;
    }
    _grant = grant;

    if (args.kind == TitleKind.series) _loadShow();

    var startAt = Duration.zero;
    if (!args.fromBeginning) {
      try {
        final saved = await _history.forContent(args.contentId);
        if (saved != null && !saved.completed) {
          final nearEnd = saved.durationSeconds > 0 &&
              saved.positionSeconds >= saved.durationSeconds - 10;
          if (!nearEnd) startAt = Duration(seconds: saved.positionSeconds);
        }
      } on ApiException {
        // Without saved progress playback starts from the beginning.
      }
    }
    if (!mounted || _closed) return;

    _heartbeatTimer = Timer.periodic(_heartbeatInterval, (_) => _heartbeat());
    await _openVideo(AppConfig.resolveStreamUrl(grant.streamUrl), startAt: startAt);
  }

  void _handleRequestError(ApiException e) {
    final message = e.message;
    final lower = message.toLowerCase();
    if (lower.contains('active subscription')) {
      context.pushReplacement('/plans', extra: message);
      return;
    }
    if (e.statusCode == 403) {
      _fail(message, _FailureKind.device);
    } else if (e.statusCode == 409) {
      _fail(message, _FailureKind.notReady);
    } else {
      _fail(message, _FailureKind.generic);
    }
  }

  Future<void> _openVideo(String url, {required Duration startAt}) async {
    final uri = Uri.parse(url);
    final isHls = uri.path.toLowerCase().endsWith('.m3u8');
    final controller = VideoPlayerController.networkUrl(
      uri,
      formatHint: isHls ? VideoFormat.hls : null,
    );
    _video = controller;
    setState(() => _status = 'Loading video…');
    try {
      await controller.initialize();
    } on Object catch (e) {
      if (!mounted || _closed) return;
      _fail(_videoErrorText(e), _FailureKind.generic);
      return;
    }
    if (!mounted || _closed) return;
    if (startAt > Duration.zero && startAt < controller.value.duration) {
      await controller.seekTo(startAt);
    }
    controller.addListener(_onVideoTick);
    await controller.play();
    if (!mounted) return;
    setState(() => _status = null);
    _progressTimer = Timer.periodic(_progressInterval, (_) => _reportProgress());
    _scheduleHide();
  }

  String _videoErrorText(Object e) {
    if (e is PlatformException) {
      return 'The video could not be played${e.message == null ? '' : ': ${e.message}'}';
    }
    return 'The video could not be played.';
  }

  Future<void> _loadShow() async {
    try {
      final show = await ref.read(catalogRepositoryProvider).tvShow(args.titleId);
      if (!mounted) return;
      _show = show;
      _nextEpisode = show.nextEpisodeAfter(args.contentId);
    } on ApiException {
      _nextEpisode = null;
    }
  }

  void _fail(String message, _FailureKind kind) {
    if (!mounted) return;
    setState(() {
      _failure = message;
      _failureKind = kind;
      _status = null;
    });
  }

  // ------------------------------------------------------------- reporting

  Duration _duration(VideoPlayerValue v) {
    if (v.duration > Duration.zero) return v.duration;
    final s = _grant?.durationSeconds;
    return s == null ? Duration.zero : Duration(seconds: s);
  }

  void _reportProgress({bool force = false, Duration? positionOverride}) {
    if (args.isTrailer) return;
    final video = _video;
    if (video == null || !video.value.isInitialized) return;
    final duration = _duration(video.value);
    if (duration <= Duration.zero) return;
    final position = positionOverride ?? video.value.position;
    final second = position.inSeconds;
    if (!force && second == _lastReportedSecond) return;
    _lastReportedSecond = second;
    _history
        .saveProgress(
          contentId: args.contentId,
          titleId: args.titleId,
          titleType: args.kind.apiValue,
          positionSeconds: math.min(second, duration.inSeconds),
          durationSeconds: duration.inSeconds,
        )
        .then<void>((_) {}, onError: (Object e) {
      debugPrint('Progress not saved: ${errorMessage(e)}');
    });
  }

  void _heartbeat() {
    final grant = _grant;
    if (grant == null) return;
    _playback.heartbeat(grant.sessionId).then<void>((_) {},
        onError: (Object e) => debugPrint('Heartbeat failed: ${errorMessage(e)}'));
  }

  void _shutdown() {
    if (_closed) return;
    _closed = true;
    _progressTimer?.cancel();
    _heartbeatTimer?.cancel();
    _hideTimer?.cancel();
    _countdownTimer?.cancel();
    final video = _video;
    if (video != null) {
      video.removeListener(_onVideoTick);
      _reportProgress(force: true);
      video.dispose();
    }
    final grant = _grant;
    if (grant != null) {
      _playback.stop(grant.sessionId).then<void>((_) {},
          onError: (Object e) => debugPrint('Stop failed: ${errorMessage(e)}'));
    }
  }

  // -------------------------------------------------------------- playback

  void _onVideoTick() {
    final video = _video;
    if (video == null || _closed) return;
    final v = video.value;
    if (v.hasError && _failure == null) {
      _fail(v.errorDescription ?? 'Playback failed.', _FailureKind.generic);
      return;
    }
    if (_wasPlaying && !v.isPlaying && !_ended) {
      _reportProgress(force: true);
      _showControls(autoHide: false);
    }
    _wasPlaying = v.isPlaying;

    final duration = _duration(v);
    if (!_ended &&
        duration > Duration.zero &&
        v.position >= duration - const Duration(milliseconds: 400) &&
        !v.isPlaying) {
      _ended = true;
      _onEnded(duration);
    }
  }

  void _onEnded(Duration duration) {
    _reportProgress(force: true, positionOverride: duration);
    final profile = ref.read(currentProfileProvider);
    final next = _nextEpisode;
    if (args.kind == TitleKind.series && next != null && (profile?.autoplayNext ?? false)) {
      setState(() => _countdown = _autoplayCountdown);
      _countdownTimer = Timer.periodic(const Duration(seconds: 1), (t) {
        if (!mounted) {
          t.cancel();
          return;
        }
        final left = (_countdown ?? 0) - 1;
        if (left <= 0) {
          t.cancel();
          _playNext();
        } else {
          setState(() => _countdown = left);
        }
      });
    } else {
      setState(() => _controlsVisible = true);
    }
  }

  void _playNext() {
    final next = _nextEpisode;
    final show = _show;
    if (next == null || show == null) return;
    _countdownTimer?.cancel();
    context.pushReplacement('/play', extra: PlayerArgs.episode(show: show.show, episode: next));
  }

  void _cancelAutoplay() {
    _countdownTimer?.cancel();
    setState(() {
      _countdown = null;
      _controlsVisible = true;
    });
  }

  void _togglePlay() {
    final video = _video;
    if (video == null) return;
    if (video.value.isPlaying) {
      video.pause();
    } else {
      if (_ended) {
        _ended = false;
        video.seekTo(Duration.zero);
      }
      video.play();
      _scheduleHide();
    }
  }

  void _seekBy(Duration delta) {
    final video = _video;
    if (video == null) return;
    final duration = _duration(video.value);
    var target = video.value.position + delta;
    if (target < Duration.zero) target = Duration.zero;
    if (duration > Duration.zero && target > duration) target = duration;
    video.seekTo(target);
    if (target < duration) _ended = false;
    _scheduleHide();
  }

  void _showControls({bool autoHide = true}) {
    if (!mounted) return;
    setState(() => _controlsVisible = true);
    if (autoHide) _scheduleHide();
  }

  void _scheduleHide() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 4), () {
      if (mounted && (_video?.value.isPlaying ?? false) && _dragValue == null) {
        setState(() => _controlsVisible = false);
      }
    });
  }

  Future<void> _signDeviceInAgain() async {
    setState(() => _busyAction = true);
    try {
      await ref.read(sessionControllerProvider.notifier).reRegisterDevice();
      if (!mounted) return;
      setState(() => _busyAction = false);
      _start();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _busyAction = false;
        _failure = e.message;
      });
    }
  }

  // -------------------------------------------------------------------- UI

  String get _subtitle {
    final ep = args.episode;
    if (ep == null) return '';
    return '${ep.label}  ${ep.title}';
  }

  @override
  Widget build(BuildContext context) {
    final video = _video;
    return Scaffold(
      backgroundColor: Colors.black,
      body: _failure != null
          ? _buildFailure()
          : GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => _controlsVisible
                  ? setState(() => _controlsVisible = false)
                  : _showControls(),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (video != null && video.value.isInitialized)
                    Center(
                      child: AspectRatio(
                        aspectRatio: video.value.aspectRatio == 0 ? 16 / 9 : video.value.aspectRatio,
                        child: VideoPlayer(video),
                      ),
                    ),
                  if (_status != null)
                    Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const CircularProgressIndicator(),
                          const SizedBox(height: 14),
                          Text(_status!, style: const TextStyle(color: Colors.white70)),
                        ],
                      ),
                    ),
                  if (video != null && video.value.isInitialized)
                    ValueListenableBuilder<VideoPlayerValue>(
                      valueListenable: video,
                      builder: (context, value, _) => Stack(
                        fit: StackFit.expand,
                        children: [
                          if (value.isBuffering && value.isPlaying)
                            const Center(child: CircularProgressIndicator()),
                          AnimatedOpacity(
                            opacity: _controlsVisible ? 1 : 0,
                            duration: const Duration(milliseconds: 200),
                            child: IgnorePointer(
                              ignoring: !_controlsVisible,
                              child: _buildControls(value),
                            ),
                          ),
                        ],
                      ),
                    ),
                  if (_status != null || video == null || !video.value.isInitialized)
                    _topBar(),
                  if (_countdown != null && _nextEpisode != null) _buildNextEpisode(),
                ],
              ),
            ),
    );
  }

  Widget _topBar() {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(4, 8, 16, 0),
          child: Row(
            children: [
              IconButton(
                icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
                onPressed: () => context.pop(),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      args.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white),
                    ),
                    if (_subtitle.isNotEmpty)
                      Text(
                        _subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildControls(VideoPlayerValue value) {
    final duration = _duration(value);
    final totalMs = math.max(duration.inMilliseconds, 1).toDouble();
    final positionMs = _dragValue ?? value.position.inMilliseconds.clamp(0, totalMs.toInt()).toDouble();
    var bufferedMs = 0;
    for (final r in value.buffered) {
      bufferedMs = math.max(bufferedMs, r.end.inMilliseconds);
    }
    final position = Duration(milliseconds: positionMs.toInt());
    final remaining = duration - position;

    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xAA000000), Color(0x33000000), Color(0xAA000000)],
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          _topBar(),
          Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  iconSize: 40,
                  color: Colors.white,
                  icon: const Icon(Icons.replay_10_rounded),
                  onPressed: () => _seekBy(const Duration(seconds: -10)),
                ),
                const SizedBox(width: 36),
                IconButton(
                  iconSize: 64,
                  color: Colors.white,
                  icon: Icon(value.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded),
                  onPressed: _togglePlay,
                ),
                const SizedBox(width: 36),
                IconButton(
                  iconSize: 40,
                  color: Colors.white,
                  icon: const Icon(Icons.forward_10_rounded),
                  onPressed: () => _seekBy(const Duration(seconds: 10)),
                ),
              ],
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: Row(
                  children: [
                    Text(formatClock(position), style: const TextStyle(color: Colors.white, fontSize: 13)),
                    Expanded(
                      child: SizedBox(
                        height: 36,
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 24),
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(2),
                                child: LinearProgressIndicator(
                                  value: (bufferedMs / totalMs).clamp(0.0, 1.0),
                                  minHeight: 3,
                                  backgroundColor: Colors.white24,
                                  color: Colors.white54,
                                ),
                              ),
                            ),
                            SliderTheme(
                              data: SliderTheme.of(context).copyWith(
                                trackHeight: 3,
                                activeTrackColor: AppColors.accent,
                                inactiveTrackColor: Colors.transparent,
                                thumbColor: AppColors.accent,
                                overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                              ),
                              child: Slider(
                                min: 0,
                                max: totalMs,
                                value: positionMs.clamp(0, totalMs),
                                onChangeStart: (v) {
                                  _hideTimer?.cancel();
                                  setState(() => _dragValue = v);
                                },
                                onChanged: (v) => setState(() => _dragValue = v),
                                onChangeEnd: (v) async {
                                  await _video?.seekTo(Duration(milliseconds: v.toInt()));
                                  if (!mounted) return;
                                  setState(() => _dragValue = null);
                                  if (v < totalMs) _ended = false;
                                  _scheduleHide();
                                },
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    Text(
                      '-${formatClock(remaining.isNegative ? Duration.zero : remaining)}',
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNextEpisode() {
    final next = _nextEpisode!;
    return Positioned(
      right: 24,
      bottom: 72,
      child: Container(
        width: 300,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.surfaceHigh.withValues(alpha: 0.95),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Next episode in $_countdown',
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 13)),
            const SizedBox(height: 4),
            Text('${next.label}  ${next.title}',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700, color: Colors.white)),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: FilledButton(
                    onPressed: _playNext,
                    style: FilledButton.styleFrom(minimumSize: const Size(0, 40)),
                    child: const Text('Play now'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: _cancelAutoplay,
                    style: OutlinedButton.styleFrom(minimumSize: const Size(0, 40)),
                    child: const Text('Cancel'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFailure() {
    final IconData icon;
    final String title;
    switch (_failureKind) {
      case _FailureKind.device:
        icon = Icons.phonelink_erase_rounded;
        title = 'This device is signed out';
      case _FailureKind.notReady:
        icon = Icons.hourglass_empty_rounded;
        title = 'Not available yet';
      case _FailureKind.blocked:
        icon = Icons.lock_outline_rounded;
        title = 'Not available on this profile';
      case _FailureKind.generic:
        icon = Icons.error_outline_rounded;
        title = "Can't play this title";
    }
    return Stack(
      children: [
        Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon, size: 48, color: Colors.white70),
                  const SizedBox(height: 14),
                  Text(title,
                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white)),
                  const SizedBox(height: 8),
                  Text(_failure!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white70, height: 1.4)),
                  const SizedBox(height: 20),
                  Wrap(
                    spacing: 12,
                    runSpacing: 8,
                    alignment: WrapAlignment.center,
                    children: [
                      if (_failureKind == _FailureKind.device)
                        FilledButton(
                          onPressed: _busyAction ? null : _signDeviceInAgain,
                          child: _busyAction
                              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                              : const Text('Sign this device in again'),
                        ),
                      if (_failureKind == _FailureKind.generic || _failureKind == _FailureKind.notReady)
                        FilledButton(
                          onPressed: () {
                            _reset();
                            _start();
                          },
                          child: const Text('Retry'),
                        ),
                      OutlinedButton(
                        onPressed: () => context.pop(),
                        child: const Text('Back'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  /// Releases the previous attempt before retrying.
  void _reset() {
    _progressTimer?.cancel();
    _heartbeatTimer?.cancel();
    final video = _video;
    _video = null;
    if (video != null) {
      video.removeListener(_onVideoTick);
      video.dispose();
    }
    final grant = _grant;
    _grant = null;
    if (grant != null) {
      _playback.stop(grant.sessionId).ignore();
    }
    _ended = false;
    _lastReportedSecond = -1;
  }
}
