import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/utils/format.dart';
import '../player_logic.dart';

/// Back button, title and "S1:E3 Title", with optional [actions] on the right.
class PlayerTopBar extends StatelessWidget {
  const PlayerTopBar({
    super.key,
    required this.title,
    required this.subtitle,
    required this.onBack,
    this.actions = const [],
  });

  final String title;
  final String subtitle;
  final VoidCallback onBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(4, 8, 12, 0),
          child: Row(
            children: [
              IconButton(
                tooltip: 'Back',
                icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
                onPressed: onBack,
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white),
                    ),
                    if (subtitle.isNotEmpty)
                      Text(
                        subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                  ],
                ),
              ),
              ...actions,
            ],
          ),
        ),
      ),
    );
  }
}

class PlaybackSpeedButton extends StatelessWidget {
  const PlaybackSpeedButton({
    super.key,
    required this.speed,
    required this.onSelected,
    required this.onOpened,
    required this.onClosed,
  });

  final double speed;
  final ValueChanged<double> onSelected;
  final VoidCallback onOpened;
  final VoidCallback onClosed;

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<double>(
      tooltip: 'Playback speed',
      initialValue: speed,
      color: AppColors.surfaceHigh,
      onOpened: onOpened,
      onCanceled: onClosed,
      onSelected: (s) {
        onSelected(s);
        onClosed();
      },
      itemBuilder: (_) => [
        for (final s in playbackSpeeds)
          CheckedPopupMenuItem<double>(
            value: s,
            checked: s == speed,
            child: Text(s == 1.0 ? '1× (Normal)' : speedLabel(s)),
          ),
      ],
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.speed_rounded, color: Colors.white, size: 22),
            const SizedBox(width: 6),
            Text(
              'Speed (${speedLabel(speed)})',
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

/// Centre transport (−10 / play-pause / +10) and the bottom scrubber row.
/// The top bar is passed in so the screen decides its actions.
class PlayerControls extends StatelessWidget {
  const PlayerControls({
    super.key,
    required this.value,
    required this.duration,
    required this.dragValue,
    required this.topBar,
    required this.onTogglePlay,
    required this.onSeekBackward,
    required this.onSeekForward,
    required this.onScrubStart,
    required this.onScrub,
    required this.onScrubEnd,
    this.onNextEpisode,
  });

  final VideoPlayerValue value;
  final Duration duration;

  /// Scrubber position (ms) while the user drags, otherwise null.
  final double? dragValue;
  final Widget topBar;
  final VoidCallback onTogglePlay;
  final VoidCallback onSeekBackward;
  final VoidCallback onSeekForward;
  final ValueChanged<double> onScrubStart;
  final ValueChanged<double> onScrub;
  final ValueChanged<double> onScrubEnd;
  final VoidCallback? onNextEpisode;

  @override
  Widget build(BuildContext context) {
    final totalMs = math.max(duration.inMilliseconds, 1).toDouble();
    final positionMs = dragValue ?? value.position.inMilliseconds.clamp(0, totalMs.toInt()).toDouble();
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
          topBar,
          Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: 'Back 10 seconds',
                  iconSize: 44,
                  color: Colors.white,
                  icon: const Icon(Icons.replay_10_rounded),
                  onPressed: onSeekBackward,
                ),
                const SizedBox(width: 40),
                IconButton(
                  tooltip: value.isPlaying ? 'Pause' : 'Play',
                  iconSize: 68,
                  color: Colors.white,
                  icon: Icon(value.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded),
                  onPressed: onTogglePlay,
                ),
                const SizedBox(width: 40),
                IconButton(
                  tooltip: 'Forward 10 seconds',
                  iconSize: 44,
                  color: Colors.white,
                  icon: const Icon(Icons.forward_10_rounded),
                  onPressed: onSeekForward,
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
                padding: const EdgeInsets.fromLTRB(16, 0, 12, 10),
                child: Row(
                  children: [
                    Text(formatClock(position), style: const TextStyle(color: Colors.white, fontSize: 13)),
                    Expanded(
                      child: _Scrubber(
                        totalMs: totalMs,
                        positionMs: positionMs,
                        bufferedMs: bufferedMs,
                        onStart: onScrubStart,
                        onChanged: onScrub,
                        onEnd: onScrubEnd,
                      ),
                    ),
                    Text(
                      '-${formatClock(remaining.isNegative ? Duration.zero : remaining)}',
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                    ),
                    if (onNextEpisode != null) ...[
                      const SizedBox(width: 8),
                      TextButton.icon(
                        onPressed: onNextEpisode,
                        style: TextButton.styleFrom(foregroundColor: Colors.white),
                        icon: const Icon(Icons.skip_next_rounded),
                        label: const Text('Next Episode', style: TextStyle(fontWeight: FontWeight.w600)),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Scrubber extends StatelessWidget {
  const _Scrubber({
    required this.totalMs,
    required this.positionMs,
    required this.bufferedMs,
    required this.onStart,
    required this.onChanged,
    required this.onEnd,
  });

  final double totalMs;
  final double positionMs;
  final int bufferedMs;
  final ValueChanged<double> onStart;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onEnd;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
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
              onChangeStart: onStart,
              onChanged: onChanged,
              onChangeEnd: onEnd,
            ),
          ),
        ],
      ),
    );
  }
}

/// Shown over everything while the screen is locked: taps only reveal the
/// unlock button.
class ScreenLockLayer extends StatelessWidget {
  const ScreenLockLayer({
    super.key,
    required this.hintVisible,
    required this.onTap,
    required this.onUnlock,
  });

  final bool hintVisible;
  final VoidCallback onTap;
  final VoidCallback onUnlock;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: AnimatedOpacity(
        opacity: hintVisible ? 1 : 0,
        duration: const Duration(milliseconds: 200),
        child: IgnorePointer(
          ignoring: !hintVisible,
          child: SafeArea(
            child: Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Material(
                      color: Colors.white,
                      shape: const CircleBorder(),
                      child: IconButton(
                        tooltip: 'Unlock screen',
                        iconSize: 30,
                        color: Colors.black,
                        icon: const Icon(Icons.lock_rounded),
                        onPressed: onUnlock,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text('Screen locked', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                    const Text('Tap the lock to unlock', style: TextStyle(color: Colors.white70, fontSize: 12)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
