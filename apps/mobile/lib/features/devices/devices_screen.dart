import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../data/models/misc_models.dart';
import '../../widgets/state_views.dart';

final devicesProvider = FutureProvider.autoDispose<List<Device>>(
  (ref) => ref.watch(deviceRepositoryProvider).list(),
);

class DevicesScreen extends ConsumerStatefulWidget {
  const DevicesScreen({super.key});

  @override
  ConsumerState<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends ConsumerState<DevicesScreen> {
  String? _busyId;

  Future<void> _act(Device device, {required bool remove}) async {
    final isThis = device.id == ref.read(sessionStoreProvider).deviceId;
    final ok = await confirm(
      context,
      title: remove ? 'Remove device?' : 'Sign out device?',
      message: [
        remove
            ? '"${device.deviceName ?? 'This device'}" will be removed from your account.'
            : '"${device.deviceName ?? 'This device'}" will be signed out and can no longer play titles.',
        if (isThis) 'This is the device you are using now; playback here will stop working until it is signed in again.',
      ].join('\n\n'),
      confirmLabel: remove ? 'Remove' : 'Sign out',
      destructive: true,
    );
    if (!ok || !mounted) return;
    setState(() => _busyId = device.id);
    try {
      final repo = ref.read(deviceRepositoryProvider);
      if (remove) {
        await repo.remove(device.id);
      } else {
        await repo.revoke(device.id);
      }
      if (!mounted) return;
      showMessage(context, remove ? 'Device removed.' : 'Device signed out.');
      ref.invalidate(devicesProvider);
    } on ApiException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final devices = ref.watch(devicesProvider);
    final thisDeviceId = ref.watch(sessionStoreProvider).deviceId;
    return Scaffold(
      appBar: AppBar(title: const Text('Devices')),
      body: devices.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(devicesProvider)),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(
              icon: Icons.devices_other_rounded,
              title: 'No devices registered',
              message: 'Devices appear here after they sign in to StreamX.',
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.refresh(devicesProvider.future),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: list.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, i) {
                final d = list[i];
                final isThis = d.id == thisDeviceId;
                final busy = _busyId == d.id;
                return Card(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
                    child: Row(
                      children: [
                        Icon(_iconFor(d.deviceType), color: AppColors.textSecondary),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Flexible(
                                    child: Text(
                                      d.deviceName ?? 'Unnamed device',
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(fontWeight: FontWeight.w700),
                                    ),
                                  ),
                                  if (isThis) ...[
                                    const SizedBox(width: 8),
                                    const _Tag('This device', AppColors.accent),
                                  ],
                                ],
                              ),
                              const SizedBox(height: 4),
                              Text(
                                [
                                  humanizeEnum(d.deviceType),
                                  if (d.platform != null) d.platform!,
                                  if (d.appVersion != null) 'v${d.appVersion}',
                                ].join(' · '),
                                style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                'Last active ${formatDateTime(d.lastSeenAt ?? d.registeredAt)}',
                                style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                              ),
                              const SizedBox(height: 6),
                              _Tag(
                                d.isActive ? 'Active' : humanizeEnum(d.status),
                                d.isActive ? AppColors.success : AppColors.textMuted,
                              ),
                            ],
                          ),
                        ),
                        if (busy)
                          const Padding(
                            padding: EdgeInsets.all(12),
                            child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        else
                          PopupMenuButton<String>(
                            onSelected: (v) => _act(d, remove: v == 'remove'),
                            itemBuilder: (_) => [
                              if (d.isActive)
                                const PopupMenuItem(value: 'revoke', child: Text('Sign out device')),
                              const PopupMenuItem(value: 'remove', child: Text('Remove device')),
                            ],
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  static IconData _iconFor(String? type) {
    switch (type?.toUpperCase()) {
      case 'TV':
        return Icons.tv_rounded;
      case 'TABLET':
        return Icons.tablet_android_rounded;
      case 'LAPTOP':
        return Icons.laptop_rounded;
      case 'CONSOLE':
        return Icons.sports_esports_rounded;
      default:
        return Icons.smartphone_rounded;
    }
  }
}

class _Tag extends StatelessWidget {
  const _Tag(this.text, this.color);

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(text, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w700)),
    );
  }
}
