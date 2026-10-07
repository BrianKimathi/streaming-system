import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';

class DeviceBlockedScreen extends ConsumerStatefulWidget {
  const DeviceBlockedScreen({super.key});

  @override
  ConsumerState<DeviceBlockedScreen> createState() => _DeviceBlockedScreenState();
}

class _DeviceBlockedScreenState extends ConsumerState<DeviceBlockedScreen> {
  bool _retrying = false;

  Future<void> _retry() async {
    setState(() => _retrying = true);
    try {
      await ref.read(sessionControllerProvider.notifier).retryDeviceRegistration();
    } finally {
      if (mounted) setState(() => _retrying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final message = ref.watch(sessionControllerProvider.select((s) => s.message));
    final email = ref.watch(sessionControllerProvider.select((s) => s.email));
    final isLimit = (message ?? '').toLowerCase().contains('device limit');
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.devices_other_rounded, size: 64, color: AppColors.accent),
                const SizedBox(height: 20),
                Text(
                  isLimit ? 'Too many devices' : "This device couldn't be registered",
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 12),
                Text(
                  message ?? 'StreamX could not register this device.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.textSecondary, height: 1.4),
                ),
                if (isLimit) ...[
                  const SizedBox(height: 10),
                  const Text(
                    'Your plan limits how many devices can be signed in at once. '
                    'Sign out or remove a device you no longer use, then try again.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.textSecondary, height: 1.4),
                  ),
                ],
                const SizedBox(height: 28),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () => context.push('/devices'),
                    icon: const Icon(Icons.manage_accounts_rounded),
                    label: const Text('Manage devices'),
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton(
                    onPressed: _retrying ? null : _retry,
                    child: _retrying
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Try again'),
                  ),
                ),
                const SizedBox(height: 10),
                TextButton(
                  onPressed: () => ref.read(sessionControllerProvider.notifier).logout(),
                  child: Text(email == null ? 'Sign out' : 'Sign out of $email'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
