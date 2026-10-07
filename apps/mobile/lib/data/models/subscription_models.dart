import '../../core/utils/json.dart';

class Plan {
  const Plan({
    required this.id,
    required this.name,
    required this.price,
    this.description,
    this.currency = 'KES',
    this.billingInterval,
    this.version,
    this.active = true,
    this.maxProfiles,
    this.maxRegisteredDevices,
    this.maxConcurrentStreams,
    this.maxResolution,
    this.hdrEnabled = false,
    this.audioQuality,
    this.downloadsEnabled = false,
    this.maxDownloadDevices,
    this.kidsProfilesEnabled = false,
  });

  final String id;
  final String name;
  final String? description;
  final double price;
  final String currency;
  final String? billingInterval;
  final int? version;
  final bool active;
  final int? maxProfiles;
  final int? maxRegisteredDevices;
  final int? maxConcurrentStreams;
  final String? maxResolution;
  final bool hdrEnabled;
  final String? audioQuality;
  final bool downloadsEnabled;
  final int? maxDownloadDevices;
  final bool kidsProfilesEnabled;

  bool get isFree => price <= 0;

  factory Plan.fromJson(Json json) => Plan(
        id: reqStr(json, 'id'),
        name: str(json['name']) ?? '',
        description: str(json['description']),
        price: doubleOrNull(json['price']) ?? 0,
        currency: str(json['currency']) ?? 'KES',
        billingInterval: str(json['billingInterval']),
        version: intOrNull(json['version']),
        active: boolOr(json['active'], true),
        maxProfiles: intOrNull(json['maxProfiles']),
        maxRegisteredDevices: intOrNull(json['maxRegisteredDevices']),
        maxConcurrentStreams: intOrNull(json['maxConcurrentStreams']),
        maxResolution: str(json['maxResolution']),
        hdrEnabled: boolOr(json['hdrEnabled']),
        audioQuality: str(json['audioQuality']),
        downloadsEnabled: boolOr(json['downloadsEnabled']),
        maxDownloadDevices: intOrNull(json['maxDownloadDevices']),
        kidsProfilesEnabled: boolOr(json['kidsProfilesEnabled']),
      );
}

class Entitlements {
  const Entitlements({
    this.subscriptionId,
    this.status,
    this.maxProfiles,
    this.maxRegisteredDevices,
    this.maxConcurrentStreams,
    this.maxResolution,
    this.hdrEnabled = false,
    this.audioQuality,
    this.downloadsEnabled = false,
    this.maxDownloadDevices,
    this.kidsProfilesEnabled = false,
  });

  final String? subscriptionId;
  final String? status;
  final int? maxProfiles;
  final int? maxRegisteredDevices;
  final int? maxConcurrentStreams;
  final String? maxResolution;
  final bool hdrEnabled;
  final String? audioQuality;
  final bool downloadsEnabled;
  final int? maxDownloadDevices;
  final bool kidsProfilesEnabled;

  factory Entitlements.fromJson(Json json) => Entitlements(
        subscriptionId: str(json['subscriptionId']),
        status: str(json['status']),
        maxProfiles: intOrNull(json['maxProfiles']),
        maxRegisteredDevices: intOrNull(json['maxRegisteredDevices']),
        maxConcurrentStreams: intOrNull(json['maxConcurrentStreams']),
        maxResolution: str(json['maxResolution']),
        hdrEnabled: boolOr(json['hdrEnabled']),
        audioQuality: str(json['audioQuality']),
        downloadsEnabled: boolOr(json['downloadsEnabled']),
        maxDownloadDevices: intOrNull(json['maxDownloadDevices']),
        kidsProfilesEnabled: boolOr(json['kidsProfilesEnabled']),
      );
}

class Subscription {
  const Subscription({
    required this.id,
    required this.status,
    this.accountId,
    this.plan,
    this.currentPeriodStart,
    this.currentPeriodEnd,
    this.cancelAtPeriodEnd = false,
    this.entitlements,
  });

  final String id;
  final String? accountId;
  final Plan? plan;
  final String status;
  final DateTime? currentPeriodStart;
  final DateTime? currentPeriodEnd;
  final bool cancelAtPeriodEnd;
  final Entitlements? entitlements;

  bool get isActive => status.toUpperCase() == 'ACTIVE';

  factory Subscription.fromJson(Json json) => Subscription(
        id: reqStr(json, 'id'),
        accountId: str(json['accountId']),
        plan: json['plan'] is Map ? Plan.fromJson(asJson(json['plan'])) : null,
        status: str(json['status']) ?? 'UNKNOWN',
        currentPeriodStart: dateTimeOrNull(json['currentPeriodStart']),
        currentPeriodEnd: dateTimeOrNull(json['currentPeriodEnd']),
        cancelAtPeriodEnd: boolOr(json['cancelAtPeriodEnd']),
        entitlements: json['entitlements'] is Map
            ? Entitlements.fromJson(asJson(json['entitlements']))
            : null,
      );
}
