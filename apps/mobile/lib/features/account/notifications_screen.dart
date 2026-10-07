import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../widgets/state_views.dart';
import 'account_providers.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(notificationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: notifications.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(notificationsProvider)),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(
              icon: Icons.notifications_none_rounded,
              title: 'No notifications',
              message: 'Payment confirmations and account updates will show up here.',
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.refresh(notificationsProvider.future),
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: list.length,
              separatorBuilder: (_, _) => const Divider(indent: 16, endIndent: 16),
              itemBuilder: (context, i) {
                final n = list[i];
                final failed = (n.template ?? '').toUpperCase().contains('FAILED');
                return ListTile(
                  leading: Icon(
                    failed ? Icons.error_outline_rounded : Icons.notifications_rounded,
                    color: failed ? AppColors.accent : AppColors.textSecondary,
                  ),
                  title: Text(
                    n.subject ?? humanizeEnum(n.template),
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (n.body != null && n.body!.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(n.body!, style: const TextStyle(color: AppColors.textSecondary)),
                      ],
                      const SizedBox(height: 4),
                      Text(formatDateTime(n.createdAt),
                          style: const TextStyle(color: AppColors.textMuted, fontSize: 12)),
                    ],
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
