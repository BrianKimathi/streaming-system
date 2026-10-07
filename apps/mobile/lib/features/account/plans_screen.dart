import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../data/models/subscription_models.dart';
import '../../widgets/state_views.dart';
import 'account_providers.dart';

class PlansScreen extends ConsumerStatefulWidget {
  const PlansScreen({super.key, this.reason});

  /// Why the user was sent here (e.g. the playback paywall message).
  final String? reason;

  @override
  ConsumerState<PlansScreen> createState() => _PlansScreenState();
}

class _PlansScreenState extends ConsumerState<PlansScreen> {
  String? _busyPlanId;

  Future<void> _choose(Plan plan) async {
    if (!plan.isFree) {
      context.push('/checkout', extra: plan);
      return;
    }
    setState(() => _busyPlanId = plan.id);
    try {
      final sub = await ref.read(subscriptionRepositoryProvider).subscribeFree(plan.id);
      ref.invalidate(subscriptionProvider);
      if (!mounted) return;
      showMessage(context, 'You are now on the ${sub.plan?.name ?? plan.name} plan.');
      context.pop();
    } on ApiException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _busyPlanId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final plans = ref.watch(plansProvider);
    final current = ref.watch(subscriptionProvider).value;
    final currentPlanId = (current?.isActive ?? false) ? current?.plan?.id : null;

    return Scaffold(
      appBar: AppBar(title: const Text('Choose a plan')),
      body: plans.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(plansProvider)),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(
              icon: Icons.credit_card_off_outlined,
              title: 'No plans are available right now',
              message: 'Please check back later.',
            );
          }
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (widget.reason != null) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.accent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppColors.accent.withValues(alpha: 0.4)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.lock_outline_rounded, color: AppColors.accent),
                      const SizedBox(width: 12),
                      Expanded(child: Text(widget.reason!)),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],
              for (final plan in list) ...[
                _PlanCard(
                  plan: plan,
                  isCurrent: plan.id == currentPlanId,
                  busy: _busyPlanId == plan.id,
                  onChoose: _busyPlanId != null ? null : () => _choose(plan),
                ),
                const SizedBox(height: 14),
              ],
              const Text(
                'Paid plans are charged with M-Pesa. You will get a prompt on your phone to confirm.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.textMuted, fontSize: 12),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _PlanCard extends StatelessWidget {
  const _PlanCard({
    required this.plan,
    required this.isCurrent,
    required this.busy,
    required this.onChoose,
  });

  final Plan plan;
  final bool isCurrent;
  final bool busy;
  final VoidCallback? onChoose;

  @override
  Widget build(BuildContext context) {
    final features = <(IconData, String)>[
      (Icons.hd_outlined, '${resolutionLabel(plan.maxResolution)}${plan.hdrEnabled ? ' · HDR' : ''}'),
      if (plan.maxConcurrentStreams != null)
        (Icons.cast_connected_rounded,
            'Watch on ${plan.maxConcurrentStreams} screen${plan.maxConcurrentStreams == 1 ? '' : 's'} at a time'),
      if (plan.maxRegisteredDevices != null)
        (Icons.devices_rounded, 'Up to ${plan.maxRegisteredDevices} registered devices'),
      if (plan.maxProfiles != null)
        (Icons.people_alt_outlined, '${plan.maxProfiles} profile${plan.maxProfiles == 1 ? '' : 's'}'),
      (
        plan.downloadsEnabled ? Icons.download_rounded : Icons.file_download_off_outlined,
        plan.downloadsEnabled
            ? 'Downloads${plan.maxDownloadDevices != null ? ' on ${plan.maxDownloadDevices} devices' : ''}'
            : 'No downloads'
      ),
      if (plan.kidsProfilesEnabled) (Icons.child_care_rounded, 'Kids profiles'),
      if (plan.audioQuality != null && plan.audioQuality!.isNotEmpty)
        (Icons.graphic_eq_rounded, '${humanizeEnum(plan.audioQuality)} audio'),
    ];

    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: isCurrent ? AppColors.accent : AppColors.border, width: isCurrent ? 2 : 1),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(plan.name, style: Theme.of(context).textTheme.titleLarge)),
                if (isCurrent)
                  const Text('Current plan',
                      style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.w700)),
              ],
            ),
            const SizedBox(height: 4),
            Text.rich(
              TextSpan(children: [
                TextSpan(
                  text: plan.isFree ? 'Free' : formatMoney(plan.price, plan.currency),
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
                ),
                if (!plan.isFree)
                  TextSpan(
                    text: ' / ${intervalLabel(plan.billingInterval)}',
                    style: const TextStyle(color: AppColors.textSecondary),
                  ),
              ]),
            ),
            if (plan.description != null && plan.description!.trim().isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(plan.description!.trim(), style: const TextStyle(color: AppColors.textSecondary)),
            ],
            const SizedBox(height: 12),
            for (final f in features)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  children: [
                    Icon(f.$1, size: 18, color: AppColors.textSecondary),
                    const SizedBox(width: 10),
                    Expanded(child: Text(f.$2)),
                  ],
                ),
              ),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: onChoose,
                child: busy
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Text(plan.isFree
                        ? 'Start free plan'
                        : isCurrent
                            ? 'Renew with M-Pesa'
                            : 'Pay with M-Pesa'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
