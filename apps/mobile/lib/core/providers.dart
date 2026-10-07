import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/repositories/account_repositories.dart';
import '../data/repositories/auth_repository.dart';
import '../data/repositories/catalog_repository.dart';
import '../data/repositories/playback_repositories.dart';
import '../data/repositories/profile_repository.dart';
import 'api/api_client.dart';
import 'session/session_controller.dart';
import 'storage/session_store.dart';

/// Overridden in `main()` with the store loaded from secure storage.
final sessionStoreProvider = Provider<SessionStore>(
  (ref) => throw UnimplementedError('sessionStoreProvider must be overridden'),
);

final dioProvider = Provider<Dio>((ref) {
  final store = ref.watch(sessionStoreProvider);
  final dio = buildApiDio(
    store: store,
    onSessionExpired: () => ref.read(sessionControllerProvider.notifier).handleSessionExpired(),
    onProfileGone: (message) =>
        ref.read(sessionControllerProvider.notifier).handleProfileGone(message),
  );
  ref.onDispose(dio.close);
  return dio;
});

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient(ref.watch(dioProvider)));

final authRepositoryProvider = Provider((ref) => AuthRepository(ref.watch(apiClientProvider)));
final profileRepositoryProvider =
    Provider((ref) => ProfileRepository(ref.watch(apiClientProvider)));
final catalogRepositoryProvider =
    Provider((ref) => CatalogRepository(ref.watch(apiClientProvider)));
final subscriptionRepositoryProvider =
    Provider((ref) => SubscriptionRepository(ref.watch(apiClientProvider)));
final billingRepositoryProvider =
    Provider((ref) => BillingRepository(ref.watch(apiClientProvider)));
final deviceRepositoryProvider =
    Provider((ref) => DeviceRepository(ref.watch(apiClientProvider)));
final notificationRepositoryProvider =
    Provider((ref) => NotificationRepository(ref.watch(apiClientProvider)));
final playbackRepositoryProvider =
    Provider((ref) => PlaybackRepository(ref.watch(apiClientProvider)));
final watchHistoryRepositoryProvider =
    Provider((ref) => WatchHistoryRepository(ref.watch(apiClientProvider)));
final trendingRepositoryProvider =
    Provider((ref) => TrendingRepository(ref.watch(apiClientProvider)));
