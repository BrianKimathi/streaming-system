import 'package:flutter/material.dart';

import '../player_logic.dart';

/// Half-ellipse ripple on the tapped side with the accumulated seek
/// ("−20s" / "+30s"). [pulse] changes on every tap to replay the animation.
class SeekRipple extends StatelessWidget {
  const SeekRipple({super.key, required this.side, required this.seconds, required this.pulse});

  final SeekSide side;
  final int seconds;
  final int pulse;

  @override
  Widget build(BuildContext context) {
    final forward = side == SeekSide.forward;
    return IgnorePointer(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth * 0.38;
          final radius = Radius.elliptical(width, constraints.maxHeight / 2);
          return Align(
            alignment: forward ? Alignment.centerRight : Alignment.centerLeft,
            child: TweenAnimationBuilder<double>(
              key: ValueKey(pulse),
              tween: Tween(begin: 0, end: 1),
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeOut,
              builder: (context, t, child) => Container(
                width: width,
                height: constraints.maxHeight,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.08 + 0.14 * (1 - t)),
                  borderRadius: forward
                      ? BorderRadius.only(topLeft: radius, bottomLeft: radius)
                      : BorderRadius.only(topRight: radius, bottomRight: radius),
                ),
                child: Transform.scale(scale: 0.85 + 0.15 * t, child: child),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    forward ? Icons.fast_forward_rounded : Icons.fast_rewind_rounded,
                    size: 40,
                    color: Colors.white,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    seekLabel(seconds),
                    style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
