import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../data/models/misc_models.dart';
import '../../widgets/state_views.dart';
import 'account_providers.dart';

class BillingHistoryScreen extends ConsumerWidget {
  const BillingHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final history = ref.watch(billingHistoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Billing history')),
      body: history.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(billingHistoryProvider)),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(
              icon: Icons.receipt_long_outlined,
              title: 'No payments yet',
              message: 'M-Pesa payments for your plan will appear here.',
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.refresh(billingHistoryProvider.future),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: list.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, i) => _TransactionCard(tx: list[i]),
            ),
          );
        },
      ),
    );
  }
}

class _TransactionCard extends StatelessWidget {
  const _TransactionCard({required this.tx});

  final PaymentTransaction tx;

  @override
  Widget build(BuildContext context) {
    final status = tx.normalizedStatus;
    final Color color = switch (status) {
      'COMPLETED' => AppColors.success,
      'PENDING' || 'INITIATED' => AppColors.warning,
      'REFUNDED' => AppColors.textSecondary,
      _ => AppColors.accent,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    tx.planName ?? 'Plan payment',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                Text(formatMoney(tx.amount, tx.currency),
                    style: const TextStyle(fontWeight: FontWeight.w700)),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(humanizeEnum(status),
                      style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    formatDateTime(tx.createdAt),
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              [
                (tx.paymentMethod == null || tx.paymentMethod!.toUpperCase() == 'MPESA')
                    ? 'M-Pesa'
                    : humanizeEnum(tx.paymentMethod),
                if (tx.phoneNumber != null) tx.phoneNumber!,
                if (tx.externalTransactionId != null) 'Receipt ${tx.externalTransactionId}',
              ].join(' · '),
              style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
            if (tx.errorMessage != null && !tx.isCompleted) ...[
              const SizedBox(height: 6),
              Text(tx.errorMessage!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
            ],
          ],
        ),
      ),
    );
  }
}
