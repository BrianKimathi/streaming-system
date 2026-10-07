import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';

class StreamXLogo extends StatelessWidget {
  const StreamXLogo({super.key, this.size = 40});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Text.rich(
      TextSpan(
        children: [
          const TextSpan(text: 'STREAM'),
          TextSpan(text: 'X', style: TextStyle(color: AppColors.accent, fontSize: size * 1.1)),
        ],
      ),
      style: TextStyle(
        fontSize: size,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
        color: AppColors.textPrimary,
      ),
    );
  }
}

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (ref.read(sessionControllerProvider).stage == SessionStage.loading) {
        ref.read(sessionControllerProvider.notifier).bootstrap();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    final unavailable = session.stage == SessionStage.unavailable;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const StreamXLogo(),
                const SizedBox(height: 32),
                if (!unavailable)
                  const SizedBox(
                    width: 28,
                    height: 28,
                    child: CircularProgressIndicator(strokeWidth: 3),
                  )
                else ...[
                  Text(
                    session.message ?? "We couldn't start StreamX.",
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppColors.textSecondary, height: 1.4),
                  ),
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: () => ref.read(sessionControllerProvider.notifier).retryStartup(),
                    child: const Text('Retry'),
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () => ref.read(sessionControllerProvider.notifier).logout(),
                    child: const Text('Sign out'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
