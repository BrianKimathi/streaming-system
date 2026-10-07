import '../../core/utils/json.dart';

class Profile {
  const Profile({
    required this.id,
    required this.name,
    this.accountId,
    this.avatarUrl,
    this.type = 'ADULT',
    this.maturityRating,
    this.language,
    this.preferredAudio,
    this.preferredSubtitle,
    this.autoplayNext = true,
    this.pinProtected = false,
    this.createdAt,
  });

  final String id;
  final String? accountId;
  final String name;
  final String? avatarUrl;
  final String type;
  final String? maturityRating;
  final String? language;
  final String? preferredAudio;
  final String? preferredSubtitle;
  final bool autoplayNext;
  final bool pinProtected;
  final DateTime? createdAt;

  bool get isKids => type.toUpperCase() == 'KIDS';

  /// Built-in avatar palette index from the `avatar:<n>` convention.
  int? get avatarIndex {
    final a = avatarUrl;
    if (a == null || !a.startsWith('avatar:')) return null;
    return int.tryParse(a.substring('avatar:'.length));
  }

  factory Profile.fromJson(Json json) => Profile(
        id: reqStr(json, 'id'),
        accountId: str(json['accountId']),
        name: str(json['name']) ?? '',
        avatarUrl: str(json['avatarUrl']),
        type: str(json['type']) ?? 'ADULT',
        maturityRating: str(json['maturityRating']),
        language: str(json['language']),
        preferredAudio: str(json['preferredAudio']),
        preferredSubtitle: str(json['preferredSubtitle']),
        autoplayNext: boolOr(json['autoplayNext'], true),
        pinProtected: boolOr(json['pinProtected']),
        createdAt: dateTimeOrNull(json['createdAt']),
      );
}

class SelectProfileResult {
  const SelectProfileResult({required this.profileAccessToken, required this.profile});

  final String profileAccessToken;
  final Profile profile;

  factory SelectProfileResult.fromJson(Json json) => SelectProfileResult(
        profileAccessToken: reqStr(json, 'profileAccessToken'),
        profile: Profile.fromJson(asJson(json['profile'])),
      );
}

class WatchlistItem {
  const WatchlistItem({required this.titleId, required this.titleType, this.addedAt});

  final String titleId;
  final String titleType;
  final DateTime? addedAt;

  bool get isSeries => titleType.toUpperCase() == 'SERIES';

  factory WatchlistItem.fromJson(Json json) => WatchlistItem(
        titleId: reqStr(json, 'titleId'),
        titleType: str(json['titleType']) ?? 'MOVIE',
        addedAt: dateTimeOrNull(json['addedAt']),
      );
}
