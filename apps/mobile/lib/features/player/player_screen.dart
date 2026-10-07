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
import '../../core/utils/maturity.dart';
import '../../data/models/catalog_models.dart';
import '../../data/models/misc_models.dart';
import '../../data/repositories/playback_repositories.dart';
import 'player_args.dart';
import 'player_logic.dart';
import 'widgets/end_overlays.dart';
import 'widgets/episodes_panel.dart';
import 'widgets/player_controls.dart';
import 'widgets/player_failure_view.dart';
import 'widgets/seek_ripple.dart';

class PlayerScreen extends ConsumerStatefulWidget {
  const PlayerScreen({super.key, required this.args});

  final PlayerArgs args;

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> with WidgetsBindingObserver {
  static const _progressInterval = Duration(seconds: 15);
  static const _heartbeatInterval = Duration(seconds: 30);
  static const _autoplayCountdown = 5;
  static const _controlsTimeout = Duration(seconds: 4);
  static const _seekStep = Duration(seconds: 10);

  /// Rapid seeks build on the previous target: `seekTo` is asynchronous, so the
  /// reported position lags behind for a moment.
  static const _seekChainWindow = Duration(seconds: 1);

  /// Changes when episodes are switched in place; also bumped by [_reset].
  late PlayerArgs _args;

  /// Incremented whenever an attempt is abandoned so late async results of the
  /// previous attempt are discarded.
  int _generation = 0;

  VideoPlayerController? _video;
  PlaybackGrant? _grant;
  String? _status = 'Preparing playback…';
  String? _failure;
  PlayerFailureKind _failureKind = PlayerFailureKind.generic;
  bool _busyAction = false;

  Timer? _progressTimer;
  Timer? _heartbeatTimer;
  Timer? _hideTimer;
  Timer? _countdownTimer;
  Timer? _singleTapTimer;
  Timer? _seekOverlayTimer;
  Timer? _lockHintTimer;
  int? _countdown;
  bool _controlsVisible = true;
  bool _menuOpen = false;
  bool _wasPlaying = false;
  bool _ended = false;
  bool _endCardDismissed = false;
  bool _closed = false;
  int _lastReportedSecond = -1;
  double? _dragValue;
  double _speed = 1.0;
  bool _locked = false;
  bool _lockHintVisible = false;

  final _seekTaps = DoubleTapSeekAccumulator(stepSeconds: _seekStep.inSeconds);
  Duration? _lastSeekTarget;
  DateTime? _lastSeekAt;
  SeekSide? _rippleSide;
  int _rippleSeconds = 0;
  int _ripplePulse = 0;

  TvShowDetail? _show;
  Episode? _nextEpisode;
  bool _episodesOpen = false;
  Map<String, WatchProgress> _episodeProgress = const {};
  bool _episodeProgressLoading = false;
  String? _episodeProgressError;

  // Captured up front: these are also used from dispose(), where `ref` is
  // no longer usable.
  late final PlaybackRepository _playback;
  late final WatchHistoryRepository _history;

  PlayerArgs get args => _args;

  bool get _isSeries => args.kind == TitleKind.series && !args.isTrailer;

  @override
  void initState() {
    super.initState();
    _args = widget.args;
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
    if (state == AppLifecycleState.resumed) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    }
  }

  // ---------------------------------------------------------------- startup

  bool _isStale(int generation) => !mounted || _closed || generation != _generation;

  Future<void> _start() async {
    final generation = _generation;
    setState(() {
      _failure = null;
      _status = 'Preparing playback…';
    });

    if (args.isTrailer) {
      final url = AppConfig.resolveMediaUrl(args.trailerUrl) ?? args.trailerUrl!;
      await _openVideo(url, startAt: Duration.zero, generation: generation);
      return;
    }

    final profile = ref.read(currentProfileProvider);
    if (profile != null &&
        !isAllowedForProfile(
          contentRating: args.maturityRating,
          profileRating: profile.maturityRating,
          isKids: profile.isKids,
        )) {
      _fail("This title is above this profile's maturity rating.", PlayerFailureKind.blocked);
      return;
    }

    var deviceId = ref.read(sessionControllerProvider).deviceId ??
        ref.read(sessionStoreProvider).deviceId;
    if (deviceId == null) {
      try {
        await ref.read(sessionControllerProvider.notifier).reRegisterDevice();
        if (_isStale(generation)) return;
        deviceId = ref.read(sessionControllerProvider).deviceId;
      } on ApiException catch (e) {
        if (_isStale(generation)) return;
        _fail(e.message, PlayerFailureKind.generic);
        return;
      }
    }
    if (deviceId == null) {
      _fail('This device is not registered.', PlayerFailureKind.device);
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
      if (_isStale(generation)) return;
      _handleRequestError(e);
      return;
    }
    if (_isStale(generation)) {
      _playback.stop(grant.sessionId).ignore();
      return;
    }
    _grant = grant;

    if (_isSeries) _loadShow(generation);

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
    if (_isStale(generation)) return;

    _heartbeatTimer = Timer.periodic(_heartbeatInterval, (_) => _heartbeat());
    await _openVideo(AppConfig.resolveStreamUrl(grant.streamUrl), startAt: startAt, generation: generation);
  }

  void _handleRequestError(ApiException e) {
    final message = e.message;
    final lower = message.toLowerCase();
    if (lower.contains('active subscription')) {
      context.pushReplacement('/plans', extra: message);
      return;
    }
    if (e.statusCode == 403) {
      _fail(message, PlayerFailureKind.device);
    } else if (e.statusCode == 409) {
      _fail(message, PlayerFailureKind.notReady);
    } else {
      _fail(message, PlayerFailureKind.generic);
    }
  }

  Future<void> _openVideo(String url, {required Duration startAt, required int generation}) async {
    // Trailers are progressive MP4/WebM; only stream playlists get the HLS hint.
    final controller = VideoPlayerController.networkUrl(
      Uri.parse(url),
      formatHint: AppConfig.isHlsUrl(url) ? VideoFormat.hls : null,
    );
    _video = controller;
    setState(() => _status = 'Loading video…');
    try {
      await controller.initialize();
    } on Object catch (e) {
      if (_isStale(generation)) return;
      _fail(_videoErrorText(e), PlayerFailureKind.generic);
      return;
    }
    if (_isStale(generation)) return;
    if (startAt > Duration.zero && startAt < controller.value.duration) {
      await controller.seekTo(startAt);
    }
    controller.addListener(_onVideoTick);
    await controller.play();
    if (_speed != 1.0) await controller.setPlaybackSpeed(_speed);
    if (_isStale(generation)) return;
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

  Future<void> _loadShow(int generation) async {
    var show = _show;
    if (show == null || show.show.id != args.titleId) {
      try {
        show = await ref.read(catalogRepositoryProvider).tvShow(args.titleId);
      } on ApiException {
        show = null;
      }
    }
    if (_isStale(generation)) return;
    setState(() {
      _show = show;
      _nextEpisode = show?.nextEpisodeAfter(args.contentId);
    });
  }

  void _fail(String message, PlayerFailureKind kind) {
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

  void _cancelTimers() {
    _progressTimer?.cancel();
    _heartbeatTimer?.cancel();
    _hideTimer?.cancel();
    _countdownTimer?.cancel();
    _singleTapTimer?.cancel();
    _seekOverlayTimer?.cancel();
    _lockHintTimer?.cancel();
  }

  void _shutdown() {
    if (_closed) return;
    _closed = true;
    _cancelTimers();
    final video = _video;
    if (video != null) {
      video.removeListener(_onVideoTick);
      if (!_ended) _reportProgress(force: true);
      video.dispose();
    }
    final grant = _grant;
    if (grant != null) {
      _playback.stop(grant.sessionId).then<void>((_) {},
          onError: (Object e) => debugPrint('Stop failed: ${errorMessage(e)}'));
    }
  }

  /// Releases the current attempt (video, session, timers) before a retry or
  /// before switching to another episode.
  void _reset() {
    _generation++;
    _cancelTimers();
    final video = _video;
    _video = null;
    if (video != null) {
      video.removeListener(_onVideoTick);
      // Disposed after the next frame so no widget still listens to it.
      WidgetsBinding.instance.addPostFrameCallback((_) => video.dispose());
    }
    final grant = _grant;
    _grant = null;
    if (grant != null) {
      _playback.stop(grant.sessionId).ignore();
    }
    _ended = false;
    _endCardDismissed = false;
    _countdown = null;
    _wasPlaying = false;
    _dragValue = null;
    _lastReportedSecond = -1;
    _lastSeekTarget = null;
    _seekTaps.reset();
    _rippleSide = null;
  }

  // -------------------------------------------------------------- playback

  void _onVideoTick() {
    final video = _video;
    if (video == null || _closed) return;
    final v = video.value;
    if (v.hasError && _failure == null) {
      _fail(v.errorDescription ?? 'Playback failed.', PlayerFailureKind.generic);
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
    _hideTimer?.cancel();
    final autoplay = ref.read(currentProfileProvider)?.autoplayNext ?? false;
    final hasNext = _isSeries && _nextEpisode != null;
    setState(() {
      _locked = false;
      _episodesOpen = false;
      _controlsVisible = false;
      _countdown = hasNext && autoplay ? _autoplayCountdown : null;
    });
    if (_countdown == null) return;
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
  }

  /// Leaves the "ended" state after the user seeks back or restarts.
  void _clearEnded() {
    if (!_ended && _countdown == null) return;
    _countdownTimer?.cancel();
    setState(() {
      _ended = false;
      _endCardDismissed = false;
      _countdown = null;
    });
  }

  void _playNext() {
    final next = _nextEpisode;
    if (next != null) _playEpisode(next);
  }

  void _playEpisode(Episode episode) {
    final show = _show;
    if (show == null) return;
    _switchTo(PlayerArgs.episode(show: show.show, episode: episode));
  }

  /// Plays another episode in this screen (keeps landscape and the speed).
  void _switchTo(PlayerArgs next) {
    if (!_ended) _reportProgress(force: true);
    _reset();
    setState(() {
      _args = next;
      _episodesOpen = false;
      _locked = false;
      _controlsVisible = true;
      _nextEpisode = _show?.nextEpisodeAfter(next.contentId);
    });
    _start();
  }

  void _cancelAutoplay() {
    _countdownTimer?.cancel();
    setState(() {
      _countdown = null;
      _endCardDismissed = true;
      _controlsVisible = true;
    });
  }

  void _watchAgain() {
    final video = _video;
    if (video == null) return;
    _clearEnded();
    video.seekTo(Duration.zero);
    video.play();
    _showControls();
  }

  void _togglePlay() {
    final video = _video;
    if (video == null) return;
    if (video.value.isPlaying) {
      video.pause();
    } else {
      if (_ended) {
        _clearEnded();
        video.seekTo(Duration.zero);
      }
      video.play();
      _scheduleHide();
    }
  }

  void _seekBy(Duration delta) {
    final video = _video;
    if (video == null || !video.value.isInitialized) return;
    final now = DateTime.now();
    final chained = _lastSeekTarget != null &&
        _lastSeekAt != null &&
        now.difference(_lastSeekAt!) <= _seekChainWindow;
    final base = chained ? _lastSeekTarget! : video.value.position;
    final duration = _duration(video.value);
    final target = clampSeek(base, delta, duration);
    _lastSeekTarget = target;
    _lastSeekAt = now;
    video.seekTo(target);
    if (target < duration) _clearEnded();
    _scheduleHide();
  }

  void _setSpeed(double speed) {
    setState(() => _speed = speed);
    _video?.setPlaybackSpeed(speed);
  }

  // ---------------------------------------------------------------- gestures

  void _onSurfaceTap(TapUpDetails details, double width) {
    final zone = tapZoneFor(details.localPosition.dx, width);
    final ready = _video?.value.isInitialized ?? false;
    final seconds = ready ? _seekTaps.registerTap(zone, DateTime.now()) : null;
    _singleTapTimer?.cancel();
    if (seconds != null) {
      _doubleTapSeek(seconds);
    } else if (zone == TapZone.center || !ready) {
      _toggleControls();
    } else {
      // Wait to see whether this becomes a double tap.
      _singleTapTimer = Timer(_seekTaps.doubleTapWindow, _toggleControls);
    }
  }

  void _doubleTapSeek(int seconds) {
    _seekBy(Duration(seconds: seconds));
    setState(() {
      _rippleSide = _seekTaps.side;
      _rippleSeconds = _seekTaps.totalSeconds;
      _ripplePulse++;
    });
    _seekOverlayTimer?.cancel();
    _seekOverlayTimer = Timer(_seekTaps.continueWindow, () {
      if (mounted) setState(() => _rippleSide = null);
    });
  }

  void _toggleControls() {
    if (!mounted) return;
    if (_controlsVisible) {
      setState(() => _controlsVisible = false);
    } else {
      _showControls();
    }
  }

  void _showControls({bool autoHide = true}) {
    if (!mounted) return;
    setState(() => _controlsVisible = true);
    if (autoHide) _scheduleHide();
  }

  void _scheduleHide() {
    _hideTimer?.cancel();
    _hideTimer = Timer(_controlsTimeout, () {
      if (mounted && (_video?.value.isPlaying ?? false) && _dragValue == null && !_menuOpen) {
        setState(() => _controlsVisible = false);
      }
    });
  }

  void _lock() {
    _hideTimer?.cancel();
    setState(() {
      _locked = true;
      _controlsVisible = false;
    });
    _flashLockHint();
  }

  void _unlock() {
    _lockHintTimer?.cancel();
    setState(() {
      _locked = false;
      _lockHintVisible = false;
    });
    _showControls();
  }

  void _flashLockHint() {
    _lockHintTimer?.cancel();
    setState(() => _lockHintVisible = true);
    _lockHintTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _lockHintVisible = false);
    });
  }

  Future<void> _openEpisodes() async {
    _hideTimer?.cancel();
    setState(() {
      _episodesOpen = true;
      _controlsVisible = false;
      _episodeProgressLoading = true;
      _episodeProgressError = null;
    });
    final titleId = args.titleId;
    try {
      final rows = await _history.forTitle(titleId);
      if (!mounted || titleId != args.titleId) return;
      setState(() => _episodeProgress = {for (final r in rows) r.contentId: r});
    } on ApiException catch (e) {
      if (mounted) setState(() => _episodeProgressError = e.message);
    } finally {
      if (mounted) setState(() => _episodeProgressLoading = false);
    }
  }

  void _closeEpisodes() {
    setState(() => _episodesOpen = false);
    _showControls();
  }

  Future<void> _signDeviceInAgain() async {
    setState(() => _busyAction = true);
    try {
      await ref.read(sessionControllerProvider.notifier).reRegisterDevice();
      if (!mounted) return;
      setState(() => _busyAction = false);
      _reset();
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
    final ready = video != null && video.value.isInitialized;
    final next = _isSeries ? _nextEpisode : null;
    final show = _show;

    return PopScope(
      canPop: !_episodesOpen && !_locked,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_episodesOpen) {
          _closeEpisodes();
        } else if (_locked) {
          _flashLockHint();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: _failure != null
            ? PlayerFailureView(
                kind: _failureKind,
                message: _failure!,
                busy: _busyAction,
                onSignInAgain: _signDeviceInAgain,
                onRetry: () {
                  _reset();
                  _start();
                },
                onBack: () => context.pop(),
              )
            : Stack(
                fit: StackFit.expand,
                children: [
                  LayoutBuilder(
                    builder: (context, constraints) => GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTapUp: (d) => _onSurfaceTap(d, constraints.maxWidth),
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          if (ready)
                            Center(
                              child: AspectRatio(
                                aspectRatio: video.value.aspectRatio == 0 ? 16 / 9 : video.value.aspectRatio,
                                child: VideoPlayer(video),
                              ),
                            ),
                          if (_status != null) _buildStatus(),
                          if (ready) _buildPlaybackLayer(video, next),
                          if (_rippleSide != null)
                            SeekRipple(side: _rippleSide!, seconds: _rippleSeconds, pulse: _ripplePulse),
                          if (!ready) _buildTopBar(withActions: false),
                        ],
                      ),
                    ),
                  ),
                  if (ready && next != null && !_ended && !_locked && !_episodesOpen)
                    _buildNextPill(video, next),
                  if (_ended && next != null && !_endCardDismissed)
                    _buildNextEpisodeCard(next, show),
                  if (_ended && next == null && ready)
                    PlaybackEndOverlay(
                      heading: _isSeries ? "You've watched the last episode of ${args.title}" : args.title,
                      onWatchAgain: _watchAgain,
                      onBack: () => context.pop(),
                    ),
                  if (_episodesOpen && show != null) _buildEpisodesPanel(show, video),
                  if (_locked)
                    ScreenLockLayer(
                      hintVisible: _lockHintVisible,
                      onTap: _flashLockHint,
                      onUnlock: _unlock,
                    ),
                ],
              ),
      ),
    );
  }

  Widget _buildStatus() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 14),
          Text(_status!, style: const TextStyle(color: Colors.white70)),
        ],
      ),
    );
  }

  Widget _buildTopBar({required bool withActions}) {
    return PlayerTopBar(
      title: args.title,
      subtitle: _subtitle,
      onBack: () => context.pop(),
      actions: !withActions
          ? const []
          : [
              IconButton(
                tooltip: 'Lock screen',
                icon: const Icon(Icons.lock_open_rounded, color: Colors.white),
                onPressed: _lock,
              ),
              PlaybackSpeedButton(
                speed: _speed,
                onSelected: _setSpeed,
                onOpened: () {
                  _menuOpen = true;
                  _hideTimer?.cancel();
                },
                onClosed: () {
                  _menuOpen = false;
                  _scheduleHide();
                },
              ),
              if (_isSeries && _show != null)
                TextButton.icon(
                  onPressed: _openEpisodes,
                  style: TextButton.styleFrom(foregroundColor: Colors.white),
                  icon: const Icon(Icons.video_library_outlined),
                  label: const Text('Episodes', style: TextStyle(fontWeight: FontWeight.w600)),
                ),
            ],
    );
  }

  Widget _buildPlaybackLayer(VideoPlayerController video, Episode? next) {
    return ValueListenableBuilder<VideoPlayerValue>(
      valueListenable: video,
      builder: (context, value, _) {
        final showControls = _controlsVisible && !_locked && !(_ended && !_endCardDismissed);
        return Stack(
          fit: StackFit.expand,
          children: [
            if (value.isBuffering && value.isPlaying) const Center(child: CircularProgressIndicator()),
            AnimatedOpacity(
              opacity: showControls ? 1 : 0,
              duration: const Duration(milliseconds: 200),
              child: IgnorePointer(
                ignoring: !showControls,
                child: PlayerControls(
                  value: value,
                  duration: _duration(value),
                  dragValue: _dragValue,
                  topBar: _buildTopBar(withActions: true),
                  onTogglePlay: _togglePlay,
                  onSeekBackward: () => _seekBy(-_seekStep),
                  onSeekForward: () => _seekBy(_seekStep),
                  onScrubStart: (v) {
                    _hideTimer?.cancel();
                    setState(() => _dragValue = v);
                  },
                  onScrub: (v) => setState(() => _dragValue = v),
                  onScrubEnd: _onScrubEnd,
                  onNextEpisode: next == null ? null : _playNext,
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _onScrubEnd(double ms) async {
    final video = _video;
    if (video == null) return;
    final target = Duration(milliseconds: ms.toInt());
    await video.seekTo(target);
    if (!mounted) return;
    setState(() => _dragValue = null);
    if (target < _duration(video.value)) _clearEnded();
    _scheduleHide();
  }

  Widget _buildNextPill(VideoPlayerController video, Episode next) {
    return Positioned(
      right: 24,
      bottom: _controlsVisible ? 84 : 28,
      child: SafeArea(
        child: ValueListenableBuilder<VideoPlayerValue>(
          valueListenable: video,
          builder: (context, value, _) {
            final visible = shouldShowNextPill(
              position: value.position,
              duration: _duration(value),
              hasNext: true,
            );
            return visible ? NextEpisodePill(onPressed: () => _playEpisode(next)) : const SizedBox.shrink();
          },
        ),
      ),
    );
  }

  Widget _buildNextEpisodeCard(Episode next, TvShowDetail? show) {
    return Positioned.fill(
      child: ColoredBox(
        color: Colors.black.withValues(alpha: 0.55),
        child: SafeArea(
          child: Stack(
            children: [
              _buildTopBar(withActions: false),
              Positioned(
                right: 24,
                bottom: 24,
                child: NextEpisodeCard(
                  episode: next,
                  fallbackImageUrl: show?.show.backdropUrl ?? show?.show.posterUrl,
                  countdown: _countdown,
                  onPlayNow: _playNext,
                  onCancel: _cancelAutoplay,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEpisodesPanel(TvShowDetail show, VideoPlayerController? video) {
    double? currentFraction;
    if (video != null && video.value.isInitialized) {
      final duration = _duration(video.value);
      if (duration > Duration.zero) {
        currentFraction = video.value.position.inMilliseconds / duration.inMilliseconds;
      }
    }
    return Positioned.fill(
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _closeEpisodes,
              child: const ColoredBox(color: Colors.black45),
            ),
          ),
          SizedBox(
            width: math.min(440, MediaQuery.sizeOf(context).width * 0.55),
            child: EpisodesPanel(
              show: show,
              currentEpisodeId: args.contentId,
              progress: _episodeProgress,
              progressLoading: _episodeProgressLoading,
              progressError: _episodeProgressError,
              currentFraction: currentFraction,
              onSelect: _playEpisode,
              onClose: _closeEpisodes,
            ),
          ),
        ],
      ),
    );
  }
}
