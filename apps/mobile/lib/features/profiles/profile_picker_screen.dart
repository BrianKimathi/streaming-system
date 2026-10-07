import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../data/models/profile_models.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/state_views.dart';
import '../splash/splash_screen.dart';

const maxProfilesPerAccount = 5;

final profilesProvider = FutureProvider.autoDispose<List<Profile>>(
  (ref) => ref.watch(profileRepositoryProvider).list(),
);

class ProfilePickerScreen extends ConsumerWidget {
  const ProfilePickerScreen({super.key, this.manage = false});

  final bool manage;

  Future<void> _select(BuildContext context, WidgetRef ref, Profile profile) async {
    if (profile.pinProtected) {
      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (_) => PinEntryDialog(profile: profile),
      );
      return;
    }
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );
    try {
      await ref.read(sessionControllerProvider.notifier).selectProfile(profile);
      if (context.mounted) Navigator.of(context, rootNavigator: true).pop();
    } on ApiException catch (e) {
      if (!context.mounted) return;
      Navigator.of(context, rootNavigator: true).pop();
      showMessage(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profiles = ref.watch(profilesProvider);
    final notice = ref.watch(sessionControllerProvider.select((s) => s.message));
    return Scaffold(
      appBar: AppBar(
        title: manage ? const Text('Manage profiles') : const StreamXLogo(size: 22),
        automaticallyImplyLeading: manage,
        actions: [
          if (!manage)
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert),
              onSelected: (v) {
                if (v == 'logout') ref.read(sessionControllerProvider.notifier).logout();
                if (v == 'devices') context.push('/devices');
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'devices', child: Text('Devices')),
                PopupMenuItem(value: 'logout', child: Text('Sign out')),
              ],
            ),
        ],
      ),
      body: profiles.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(profilesProvider)),
        data: (list) {
          final canAdd = list.length < maxProfilesPerAccount;
          return RefreshIndicator(
            onRefresh: () => ref.refresh(profilesProvider.future),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
              children: [
                if (notice != null && !manage) ...[
                  Text(notice,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AppColors.warning)),
                  const SizedBox(height: 12),
                ],
                Text(
                  manage ? 'Tap a profile to edit it' : "Who's watching?",
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                if (list.isEmpty && !manage) ...[
                  const SizedBox(height: 8),
                  const Text(
                    'Create a profile to start watching.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.textSecondary),
                  ),
                ],
                const SizedBox(height: 28),
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 22,
                  runSpacing: 22,
                  children: [
                    for (final p in list)
                      _ProfileTile(
                        profile: p,
                        manage: manage,
                        onTap: () => manage
                            ? context.push('/profiles/edit', extra: p)
                            : _select(context, ref, p),
                      ),
                    if (canAdd)
                      _AddTile(onTap: () => context.push('/profiles/edit')),
                  ],
                ),
                if (!canAdd) ...[
                  const SizedBox(height: 20),
                  const Text(
                    'You have reached the maximum of $maxProfilesPerAccount profiles.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.textMuted, fontSize: 13),
                  ),
                ],
                const SizedBox(height: 36),
                if (!manage && list.isNotEmpty)
                  Center(
                    child: OutlinedButton.icon(
                      onPressed: () => context.push('/profiles/manage'),
                      icon: const Icon(Icons.edit_outlined, size: 18),
                      label: const Text('Manage profiles'),
                    ),
                  ),
                if (manage)
                  Center(
                    child: FilledButton(
                      onPressed: () => context.pop(),
                      child: const Text('Done'),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _ProfileTile extends StatelessWidget {
  const _ProfileTile({required this.profile, required this.manage, required this.onTap});

  final Profile profile;
  final bool manage;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 104,
        child: Column(
          children: [
            Stack(
              children: [
                ProfileAvatar(avatarUrl: profile.avatarUrl, name: profile.name, size: 96),
                if (manage)
                  Positioned.fill(
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.black45,
                        borderRadius: BorderRadius.circular(11.5),
                      ),
                      child: const Icon(Icons.edit_rounded, color: Colors.white, size: 32),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Flexible(
                  child: Text(
                    profile.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                if (profile.pinProtected) ...[
                  const SizedBox(width: 4),
                  const Icon(Icons.lock_rounded, size: 14, color: AppColors.textSecondary),
                ],
              ],
            ),
            if (profile.isKids)
              const Text('Kids', style: TextStyle(color: AppColors.textSecondary, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}

class _AddTile extends StatelessWidget {
  const _AddTile({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 104,
        child: Column(
          children: [
            Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(11.5),
                border: Border.all(color: AppColors.border, width: 2),
              ),
              child: const Icon(Icons.add_rounded, size: 44, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 8),
            const Text('Add profile', style: TextStyle(color: AppColors.textSecondary)),
          ],
        ),
      ),
    );
  }
}

/// Asks for the 4-digit PIN and performs the selection, showing the server's
/// message ("Incorrect PIN code.", lockout) inline.
class PinEntryDialog extends ConsumerStatefulWidget {
  const PinEntryDialog({super.key, required this.profile});

  final Profile profile;

  @override
  ConsumerState<PinEntryDialog> createState() => _PinEntryDialogState();
}

class _PinEntryDialogState extends ConsumerState<PinEntryDialog> {
  final _pin = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final pin = _pin.text;
    if (!RegExp(r'^\d{4}$').hasMatch(pin)) {
      setState(() => _error = 'Enter the 4-digit PIN');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(sessionControllerProvider.notifier).selectProfile(widget.profile, pin: pin);
      if (mounted) Navigator.of(context).pop();
    } on ApiException catch (e) {
      if (!mounted) return;
      _pin.clear();
      setState(() {
        _busy = false;
        _error = e.message;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Column(
        children: [
          ProfileAvatar(avatarUrl: widget.profile.avatarUrl, name: widget.profile.name, size: 56),
          const SizedBox(height: 12),
          Text('Enter PIN for ${widget.profile.name}', textAlign: TextAlign.center),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _pin,
            autofocus: true,
            enabled: !_busy,
            obscureText: true,
            keyboardType: TextInputType.number,
            maxLength: 4,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 28, letterSpacing: 16),
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(counterText: '', hintText: '••••'),
            onChanged: (v) {
              if (v.length == 4) _submit();
            },
            onSubmitted: (_) => _submit(),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFFF87171))),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _busy ? null : _submit,
          style: FilledButton.styleFrom(minimumSize: const Size(88, 40)),
          child: _busy
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Continue'),
        ),
      ],
    );
  }
}
