import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../core/api/api_exception.dart';
import '../../core/config/app_config.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../data/models/subscription_models.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/state_views.dart';
import 'account_providers.dart';

final _appVersionProvider = FutureProvider.autoDispose<String>((ref) async {
  final info = await PackageInfo.fromPlatform();
  return '${info.version} (${info.buildNumber})';
});

class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final profile = session.profile;
    final account = ref.watch(accountProvider);
    final version = ref.watch(_appVersionProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(accountProvider);
          ref.invalidate(subscriptionProvider);
          await ref.read(subscriptionProvider.future).then<void>((_) {}, onError: (_) {});
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            if (profile != null)
              Row(
                children: [
                  ProfileAvatar(avatarUrl: profile.avatarUrl, name: profile.name, size: 56),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(profile.name, style: Theme.of(context).textTheme.titleLarge),
                        Text(
                          account.value?.email ?? session.email ?? '',
                          style: const TextStyle(color: AppColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  OutlinedButton(
                    onPressed: () => ref.read(sessionControllerProvider.notifier).switchProfile(),
                    style: OutlinedButton.styleFrom(minimumSize: const Size(0, 38)),
                    child: const Text('Switch'),
                  ),
                ],
              ),
            const SizedBox(height: 20),
            const _MembershipCard(),
            const SizedBox(height: 20),
            _Tile(
              icon: Icons.tune_rounded,
              title: 'Profile settings',
              subtitle: 'Autoplay, maturity, PIN and language for ${profile?.name ?? 'this profile'}',
              onTap: () => context.push('/profile-settings'),
            ),
            _Tile(
              icon: Icons.devices_rounded,
              title: 'Devices',
              subtitle: 'Manage signed-in devices',
              onTap: () => context.push('/devices'),
            ),
            _Tile(
              icon: Icons.receipt_long_rounded,
              title: 'Billing history',
              subtitle: 'M-Pesa payments and receipts',
              onTap: () => context.push('/billing-history'),
            ),
            _Tile(
              icon: Icons.notifications_none_rounded,
              title: 'Notifications',
              onTap: () => context.push('/notifications'),
            ),
            _Tile(
              icon: Icons.lock_reset_rounded,
              title: 'Change password',
              onTap: () => context.push('/change-password'),
            ),
            _Tile(
              icon: Icons.switch_account_rounded,
              title: 'Switch profile',
              onTap: () => ref.read(sessionControllerProvider.notifier).switchProfile(),
            ),
            _Tile(
              icon: Icons.logout_rounded,
              title: 'Sign out',
              destructive: true,
              onTap: () async {
                final ok = await confirm(
                  context,
                  title: 'Sign out?',
                  message: 'You will need your email and password to sign in again.',
                  confirmLabel: 'Sign out',
                  destructive: true,
                );
                if (ok) await ref.read(sessionControllerProvider.notifier).logout();
              },
            ),
            const SizedBox(height: 24),
            Center(
              child: Text(
                'StreamX ${version.value ?? ''}\n${Uri.parse(AppConfig.apiBaseUrl).host}',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({
    required this.icon,
    required this.title,
    this.subtitle,
    required this.onTap,
    this.destructive = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final color = destructive ? AppColors.accent : AppColors.textPrimary;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      leading: Icon(icon, color: destructive ? AppColors.accent : AppColors.textSecondary),
      title: Text(title, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, style: const TextStyle(color: AppColors.textMuted, fontSize: 13)),
      trailing: destructive ? null : const Icon(Icons.chevron_right_rounded, color: AppColors.textMuted),
      onTap: onTap,
    );
  }
}

class _MembershipCard extends ConsumerStatefulWidget {
  const _MembershipCard();

  @override
  ConsumerState<_MembershipCard> createState() => _MembershipCardState();
}

class _MembershipCardState extends ConsumerState<_MembershipCard> {
  bool _busy = false;

  Future<void> _change(Subscription sub, {required bool cancel}) async {
    final end = formatDate(sub.currentPeriodEnd);
    final ok = await confirm(
      context,
      title: cancel ? 'Cancel membership?' : 'Resume membership?',
      message: cancel
          ? 'Your plan stays active until $end and will not renew after that.'
          : 'Your ${sub.plan?.name ?? ''} plan will continue after $end.',
      confirmLabel: cancel ? 'Cancel membership' : 'Resume',
      destructive: cancel,
    );
    if (!ok || !mounted) return;
    setState(() => _busy = true);
    try {
      final repo = ref.read(subscriptionRepositoryProvider);
      final updated = cancel ? await repo.cancelAtPeriodEnd() : await repo.resume();
      ref.invalidate(subscriptionProvider);
      if (mounted) {
        showMessage(
          context,
          updated.cancelAtPeriodEnd
              ? 'Membership will end on ${formatDate(updated.currentPeriodEnd)}.'
              : 'Membership resumed.',
        );
      }
    } on ApiException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final sub = ref.watch(subscriptionProvider);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: sub.when(
          loading: () => const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SkeletonBox(width: 120, height: 16),
              SizedBox(height: 10),
              SkeletonBox(width: 200, height: 22),
              SizedBox(height: 10),
              SkeletonBox(width: 160, height: 14),
            ],
          ),
          error: (e, _) => ErrorView(
            error: e,
            compact: true,
            onRetry: () => ref.invalidate(subscriptionProvider),
          ),
          data: (s) => s == null ? _noPlan(context) : _plan(context, s),
        ),
      ),
    );
  }

  Widget _noPlan(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('MEMBERSHIP', style: TextStyle(color: AppColors.textMuted, fontSize: 12, letterSpacing: 1)),
        const SizedBox(height: 6),
        Text('No plan', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 4),
        const Text('Choose a plan to start watching.', style: TextStyle(color: AppColors.textSecondary)),
        const SizedBox(height: 14),
        FilledButton(
          onPressed: () => context.push('/plans'),
          child: const Text('Choose a plan'),
        ),
      ],
    );
  }

  Widget _plan(BuildContext context, Subscription s) {
    final plan = s.plan;
    final status = s.status.toUpperCase();
    final active = status == 'ACTIVE';
    final Color statusColor = active
        ? (s.cancelAtPeriodEnd ? AppColors.warning : AppColors.success)
        : AppColors.textMuted;
    final statusText = active && s.cancelAtPeriodEnd ? 'Ending' : humanizeEnum(status);
    final String periodLine;
    if (active && s.cancelAtPeriodEnd) {
      periodLine = 'Ends on ${formatDate(s.currentPeriodEnd)}';
    } else if (active) {
      periodLine = (plan?.isFree ?? false)
          ? 'Current period ends ${formatDate(s.currentPeriodEnd)}'
          : 'Renews on ${formatDate(s.currentPeriodEnd)}';
    } else {
      periodLine = 'Ended on ${formatDate(s.currentPeriodEnd)}';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Text('MEMBERSHIP',
                style: TextStyle(color: AppColors.textMuted, fontSize: 12, letterSpacing: 1)),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(statusText,
                  style: TextStyle(color: statusColor, fontWeight: FontWeight.w700, fontSize: 12)),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(plan?.name ?? 'Plan', style: Theme.of(context).textTheme.titleLarge),
        if (plan != null)
          Text(
            plan.isFree
                ? 'Free'
                : '${formatMoney(plan.price, plan.currency)} / ${intervalLabel(plan.billingInterval)}',
            style: const TextStyle(color: AppColors.textSecondary),
          ),
        const SizedBox(height: 8),
        Text(periodLine, style: const TextStyle(color: AppColors.textSecondary)),
        if (plan != null) ...[
          const SizedBox(height: 8),
          Text(
            [
              resolutionLabel(plan.maxResolution),
              if (plan.maxConcurrentStreams != null)
                '${plan.maxConcurrentStreams} stream${plan.maxConcurrentStreams == 1 ? '' : 's'}',
              if (plan.maxRegisteredDevices != null) '${plan.maxRegisteredDevices} devices',
            ].join(' · '),
            style: const TextStyle(color: AppColors.textMuted, fontSize: 13),
          ),
        ],
        const SizedBox(height: 14),
        Wrap(
          spacing: 10,
          runSpacing: 8,
          children: [
            FilledButton(
              onPressed: () => context.push('/plans'),
              style: FilledButton.styleFrom(minimumSize: const Size(0, 42)),
              child: Text(active ? 'Change plan' : 'Choose a plan'),
            ),
            if (active && !s.cancelAtPeriodEnd)
              OutlinedButton(
                onPressed: _busy ? null : () => _change(s, cancel: true),
                style: OutlinedButton.styleFrom(minimumSize: const Size(0, 42)),
                child: const Text('Cancel membership'),
              ),
            if (active && s.cancelAtPeriodEnd)
              OutlinedButton(
                onPressed: _busy ? null : () => _change(s, cancel: false),
                style: OutlinedButton.styleFrom(minimumSize: const Size(0, 42)),
                child: const Text('Resume membership'),
              ),
          ],
        ),
      ],
    );
  }
}
