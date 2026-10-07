import 'package:flutter/material.dart';

enum PlayerFailureKind { generic, device, notReady, blocked }

class PlayerFailureView extends StatelessWidget {
  const PlayerFailureView({
    super.key,
    required this.kind,
    required this.message,
    required this.busy,
    required this.onSignInAgain,
    required this.onRetry,
    required this.onBack,
  });

  final PlayerFailureKind kind;
  final String message;
  final bool busy;
  final VoidCallback onSignInAgain;
  final VoidCallback onRetry;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final (icon, title) = switch (kind) {
      PlayerFailureKind.device => (Icons.phonelink_erase_rounded, 'This device is signed out'),
      PlayerFailureKind.notReady => (Icons.hourglass_empty_rounded, 'Not available yet'),
      PlayerFailureKind.blocked => (Icons.lock_outline_rounded, 'Not available on this profile'),
      PlayerFailureKind.generic => (Icons.error_outline_rounded, "Can't play this title"),
    };
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 460),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 48, color: Colors.white70),
              const SizedBox(height: 14),
              Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white)),
              const SizedBox(height: 8),
              Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70, height: 1.4)),
              const SizedBox(height: 20),
              Wrap(
                spacing: 12,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  if (kind == PlayerFailureKind.device)
                    FilledButton(
                      onPressed: busy ? null : onSignInAgain,
                      child: busy
                          ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Text('Sign this device in again'),
                    ),
                  if (kind == PlayerFailureKind.generic || kind == PlayerFailureKind.notReady)
                    FilledButton(onPressed: onRetry, child: const Text('Retry')),
                  OutlinedButton(onPressed: onBack, child: const Text('Back')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
