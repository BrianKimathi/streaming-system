import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/models/auth_models.dart';
import '../../data/models/misc_models.dart';
import '../../data/models/subscription_models.dart';

final accountProvider = FutureProvider.autoDispose<Account>(
  (ref) => ref.watch(authRepositoryProvider).me(),
);

/// The account's subscription; `null` when the server reports none.
final subscriptionProvider = FutureProvider.autoDispose<Subscription?>(
  (ref) => ref.watch(subscriptionRepositoryProvider).mine(),
);

final plansProvider = FutureProvider.autoDispose<List<Plan>>(
  (ref) => ref.watch(subscriptionRepositoryProvider).plans(),
);

final billingHistoryProvider = FutureProvider.autoDispose<List<PaymentTransaction>>(
  (ref) => ref.watch(billingRepositoryProvider).history(),
);

final notificationsProvider = FutureProvider.autoDispose<List<AppNotification>>(
  (ref) => ref.watch(notificationRepositoryProvider).list(),
);
