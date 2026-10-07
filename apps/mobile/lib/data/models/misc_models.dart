import '../../core/utils/json.dart';

class PaymentTransaction {
  const PaymentTransaction({
    required this.id,
    required this.status,
    this.accountId,
    this.subscriptionId,
    this.planId,
    this.planName,
    this.amount,
    this.currency,
    this.paymentMethod,
    this.phoneNumber,
    this.externalTransactionId,
    this.errorMessage,
    this.createdAt,
    this.updatedAt,
  });

  final String id;
  final String? accountId;
  final String? subscriptionId;
  final String? planId;
  final String? planName;
  final double? amount;
  final String? currency;
  final String status;
  final String? paymentMethod;
  final String? phoneNumber;
  final String? externalTransactionId;
  final String? errorMessage;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  String get normalizedStatus => status.toUpperCase();
  bool get isPending => normalizedStatus == 'PENDING' || normalizedStatus == 'INITIATED';
  bool get isCompleted => normalizedStatus == 'COMPLETED';
  bool get isFailed =>
      normalizedStatus == 'FAILED' || normalizedStatus == 'CANCELLED';

  factory PaymentTransaction.fromJson(Json json) => PaymentTransaction(
        id: reqStr(json, 'id'),
        accountId: str(json['accountId']),
        subscriptionId: str(json['subscriptionId']),
        planId: str(json['planId']),
        planName: str(json['planName']),
        amount: doubleOrNull(json['amount']),
        currency: str(json['currency']),
        status: str(json['status']) ?? 'UNKNOWN',
        paymentMethod: str(json['paymentMethod']),
        phoneNumber: str(json['phoneNumber']),
        externalTransactionId: str(json['externalTransactionId']),
        errorMessage: str(json['errorMessage']),
        createdAt: dateTimeOrNull(json['createdAt']),
        updatedAt: dateTimeOrNull(json['updatedAt']),
      );
}

class Device {
  const Device({
    required this.id,
    required this.status,
    this.accountId,
    this.deviceFingerprint,
    this.deviceName,
    this.deviceType,
    this.platform,
    this.appVersion,
    this.registeredAt,
    this.lastSeenAt,
  });

  final String id;
  final String? accountId;
  final String? deviceFingerprint;
  final String? deviceName;
  final String? deviceType;
  final String? platform;
  final String? appVersion;
  final String status;
  final DateTime? registeredAt;
  final DateTime? lastSeenAt;

  bool get isActive => status.toUpperCase() == 'ACTIVE';

  factory Device.fromJson(Json json) => Device(
        id: reqStr(json, 'id'),
        accountId: str(json['accountId']),
        deviceFingerprint: str(json['deviceFingerprint']),
        deviceName: str(json['deviceName']),
        deviceType: str(json['deviceType']),
        platform: str(json['platform']),
        appVersion: str(json['appVersion']),
        status: str(json['status']) ?? 'UNKNOWN',
        registeredAt: dateTimeOrNull(json['registeredAt']),
        lastSeenAt: dateTimeOrNull(json['lastSeenAt']),
      );
}

class PlaybackGrant {
  const PlaybackGrant({
    required this.sessionId,
    required this.contentId,
    required this.streamUrl,
    this.status,
    this.expiresAt,
    this.durationSeconds,
  });

  final String sessionId;
  final String contentId;
  final String streamUrl;
  final String? status;
  final DateTime? expiresAt;
  final int? durationSeconds;

  factory PlaybackGrant.fromJson(Json json) => PlaybackGrant(
        sessionId: reqStr(json, 'sessionId'),
        contentId: reqStr(json, 'contentId'),
        streamUrl: reqStr(json, 'streamUrl'),
        status: str(json['status']),
        expiresAt: dateTimeOrNull(json['expiresAt']),
        durationSeconds: intOrNull(json['durationSeconds']),
      );
}

class WatchProgress {
  const WatchProgress({
    required this.contentId,
    this.id,
    this.profileId,
    this.titleId,
    this.titleType,
    this.positionSeconds = 0,
    this.durationSeconds = 0,
    this.percentage = 0,
    this.completed = false,
    this.lastWatchedAt,
  });

  final String? id;
  final String? profileId;
  final String contentId;
  final String? titleId;
  final String? titleType;
  final int positionSeconds;
  final int durationSeconds;
  final double percentage;
  final bool completed;
  final DateTime? lastWatchedAt;

  /// 0..1 progress, preferring position/duration over the server percentage.
  double get fraction {
    if (durationSeconds > 0) {
      return (positionSeconds / durationSeconds).clamp(0.0, 1.0);
    }
    final p = percentage > 1 ? percentage / 100 : percentage;
    return p.clamp(0.0, 1.0);
  }

  factory WatchProgress.fromJson(Json json) => WatchProgress(
        id: str(json['id']),
        profileId: str(json['profileId']),
        contentId: str(json['contentId']) ?? str(json['episodeId']) ?? reqStr(json, 'contentId'),
        titleId: str(json['titleId']),
        titleType: str(json['titleType']),
        positionSeconds: intOrNull(json['positionSeconds']) ?? 0,
        durationSeconds: intOrNull(json['durationSeconds']) ?? 0,
        percentage: doubleOrNull(json['percentage']) ?? 0,
        completed: boolOr(json['completed']),
        lastWatchedAt: dateTimeOrNull(json['lastWatchedAt']),
      );
}

class TrendingItem {
  const TrendingItem({
    required this.contentId,
    this.title,
    this.contentType,
    this.views1h = 0,
    this.views6h = 0,
    this.completions24h = 0,
    this.likes24h = 0,
    this.velocityScore = 0,
    this.updatedAt,
  });

  final String contentId;
  final String? title;
  final String? contentType;
  final int views1h;
  final int views6h;
  final int completions24h;
  final int likes24h;
  final double velocityScore;
  final DateTime? updatedAt;

  factory TrendingItem.fromJson(Json json) => TrendingItem(
        contentId: reqStr(json, 'contentId'),
        title: str(json['title']),
        contentType: str(json['contentType']),
        views1h: intOrNull(json['views1h']) ?? 0,
        views6h: intOrNull(json['views6h']) ?? 0,
        completions24h: intOrNull(json['completions24h']) ?? 0,
        likes24h: intOrNull(json['likes24h']) ?? 0,
        velocityScore: doubleOrNull(json['velocityScore']) ?? 0,
        updatedAt: dateTimeOrNull(json['updatedAt']),
      );
}

class AppNotification {
  const AppNotification({
    required this.id,
    this.accountId,
    this.recipient,
    this.channel,
    this.template,
    this.subject,
    this.body,
    this.status,
    this.failureReason,
    this.createdAt,
  });

  final String id;
  final String? accountId;
  final String? recipient;
  final String? channel;
  final String? template;
  final String? subject;
  final String? body;
  final String? status;
  final String? failureReason;
  final DateTime? createdAt;

  factory AppNotification.fromJson(Json json) => AppNotification(
        id: reqStr(json, 'id'),
        accountId: str(json['accountId']),
        recipient: str(json['recipient']),
        channel: str(json['channel']),
        template: str(json['template']),
        subject: str(json['subject']),
        body: str(json['body']),
        status: str(json['status']),
        failureReason: str(json['failureReason']),
        createdAt: dateTimeOrNull(json['createdAt']),
      );
}
