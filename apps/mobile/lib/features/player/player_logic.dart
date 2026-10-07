import 'dart:math' as math;

/// Playback speeds offered in the player's speed menu.
const playbackSpeeds = <double>[0.5, 0.75, 1.0, 1.25, 1.5];

String speedLabel(double speed) {
  final text = speed == speed.roundToDouble() ? speed.toStringAsFixed(0) : '$speed';
  return '$text×';
}

/// Horizontal thirds of the video surface.
enum TapZone { left, center, right }

TapZone tapZoneFor(double dx, double width) {
  if (width <= 0) return TapZone.center;
  if (dx < width / 3) return TapZone.left;
  if (dx > width * 2 / 3) return TapZone.right;
  return TapZone.center;
}

enum SeekSide { backward, forward }

/// Netflix-style double-tap seeking. A double tap on the left/right third
/// seeks ten seconds; further taps on the same side while the run is active
/// (single taps are enough) keep adding ten seconds, so the overlay can show
/// the accumulated "−20s" / "+30s". Time is passed in so this stays pure.
class DoubleTapSeekAccumulator {
  DoubleTapSeekAccumulator({
    this.stepSeconds = 10,
    this.doubleTapWindow = const Duration(milliseconds: 300),
    this.continueWindow = const Duration(milliseconds: 800),
  });

  final int stepSeconds;

  /// Maximum gap between the two taps of a double tap.
  final Duration doubleTapWindow;

  /// How long after the last seek tap another tap still continues the run.
  final Duration continueWindow;

  SeekSide? _side;
  int _taps = 0;
  DateTime? _lastSeekAt;
  DateTime? _pendingTapAt;
  TapZone? _pendingZone;

  SeekSide? get side => _side;

  /// Accumulated seek of the current run in seconds (negative = backward).
  int get totalSeconds => _side == null ? 0 : (_side == SeekSide.forward ? 1 : -1) * _taps * stepSeconds;

  bool isSeeking(DateTime now) =>
      _lastSeekAt != null && now.difference(_lastSeekAt!) <= continueWindow;

  /// Registers a tap. Returns the signed seconds to seek by for this tap, or
  /// null when the tap is not (yet) a seek — the caller then treats it as a
  /// single tap (after [doubleTapWindow] for side zones).
  int? registerTap(TapZone zone, DateTime now) {
    final side = switch (zone) {
      TapZone.left => SeekSide.backward,
      TapZone.right => SeekSide.forward,
      TapZone.center => null,
    };
    if (side == null) {
      _pendingTapAt = null;
      return null;
    }
    if (isSeeking(now)) {
      _taps = _side == side ? _taps + 1 : 1;
      _side = side;
      _lastSeekAt = now;
      return _step(side);
    }
    final pending = _pendingTapAt;
    if (pending != null && _pendingZone == zone && now.difference(pending) <= doubleTapWindow) {
      _side = side;
      _taps = 1;
      _lastSeekAt = now;
      _pendingTapAt = null;
      return _step(side);
    }
    _pendingTapAt = now;
    _pendingZone = zone;
    return null;
  }

  void reset() {
    _side = null;
    _taps = 0;
    _lastSeekAt = null;
    _pendingTapAt = null;
    _pendingZone = null;
  }

  int _step(SeekSide side) => side == SeekSide.forward ? stepSeconds : -stepSeconds;
}

/// Label for an accumulated seek: "+30s" / "−20s".
String seekLabel(int seconds) => seconds < 0 ? '−${seconds.abs()}s' : '+${seconds}s';

/// Clamps `position + delta` into `[0, duration]` (no upper bound while the
/// duration is unknown).
Duration clampSeek(Duration position, Duration delta, Duration duration) {
  var target = position + delta;
  if (target < Duration.zero) target = Duration.zero;
  if (duration > Duration.zero && target > duration) target = duration;
  return target;
}

const nextPillWindow = Duration(seconds: 20);
const nextPillShortFraction = 0.03;

/// Whether the "Next Episode" pill shows: during the last 20 s, or the last
/// 3 % for short episodes where 20 s would be a large part of the episode.
/// Not once playback has ended (the end card takes over).
bool shouldShowNextPill({
  required Duration position,
  required Duration duration,
  required bool hasNext,
}) {
  if (!hasNext || duration <= Duration.zero) return false;
  final remaining = duration - position;
  if (remaining <= Duration.zero) return false;
  final shortWindowMs = (duration.inMilliseconds * nextPillShortFraction).round();
  final windowMs = math.min(nextPillWindow.inMilliseconds, shortWindowMs);
  return remaining.inMilliseconds <= windowMs;
}
