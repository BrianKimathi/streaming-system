import '../../core/api/api_client.dart';
import '../../core/api/api_exception.dart';
import '../../core/utils/json.dart';
import '../models/misc_models.dart';
import '../models/subscription_models.dart';

class SubscriptionRepository {
  SubscriptionRepository(this._api);

  final ApiClient _api;

  Future<List<Plan>> plans() async {
    final plans = await _api.get('/subscriptions/plans', parse: (d) => listOf(d, Plan.fromJson));
    return [...plans]..sort((a, b) => a.price.compareTo(b.price));
  }

  /// The account's subscription, or null when the server reports none (404).
  Future<Subscription?> mine() async {
    try {
      return await _api.get('/subscriptions/me', parse: (d) => Subscription.fromJson(asJson(d)));
    } on ApiException catch (e) {
      if (e.isNotFound) return null;
      rethrow;
    }
  }

  Future<Subscription> subscribeFree(String planId) => _api.post(
        '/subscriptions/subscribe',
        body: {'planId': planId},
        parse: (d) => Subscription.fromJson(asJson(d)),
      );

  Future<Subscription> cancelAtPeriodEnd() => _api.post(
        '/subscriptions/me/cancel',
        parse: (d) => Subscription.fromJson(asJson(d)),
      );

  Future<Subscription> resume() => _api.post(
        '/subscriptions/me/resume',
        parse: (d) => Subscription.fromJson(asJson(d)),
      );
}

class BillingRepository {
  BillingRepository(this._api);

  final ApiClient _api;

  Future<PaymentTransaction> checkout({required String planId, required String phoneNumber}) =>
      _api.post(
        '/billing/checkout',
        body: {'planId': planId, 'phoneNumber': phoneNumber},
        parse: (d) => PaymentTransaction.fromJson(asJson(d)),
      );

  Future<PaymentTransaction> transaction(String id) => _api.get(
        '/billing/transactions/$id',
        parse: (d) => PaymentTransaction.fromJson(asJson(d)),
      );

  Future<List<PaymentTransaction>> history() => _api.get(
        '/billing/history',
        parse: (d) => listOf(d, PaymentTransaction.fromJson),
      );
}

class DeviceRepository {
  DeviceRepository(this._api);

  final ApiClient _api;

  Future<Device> register({
    required String fingerprint,
    required String name,
    required String type,
    required String platform,
    required String appVersion,
  }) =>
      _api.post(
        '/devices/register',
        body: {
          'deviceFingerprint': fingerprint,
          'deviceName': name,
          'deviceType': type,
          'platform': platform,
          'appVersion': appVersion,
        },
        parse: (d) => Device.fromJson(asJson(d)),
      );

  Future<List<Device>> list() => _api.get('/devices', parse: (d) => listOf(d, Device.fromJson));

  Future<void> revoke(String id) => _api.post('/devices/$id/revoke', parse: ApiClient.ignore);

  Future<void> remove(String id) => _api.delete('/devices/$id', parse: ApiClient.ignore);
}

class NotificationRepository {
  NotificationRepository(this._api);

  final ApiClient _api;

  Future<List<AppNotification>> list() => _api.get(
        '/notifications/user',
        parse: (d) => listOf(d, AppNotification.fromJson),
      );
}
