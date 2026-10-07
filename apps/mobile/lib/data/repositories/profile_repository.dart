import '../../core/api/api_client.dart';
import '../../core/utils/json.dart';
import '../models/profile_models.dart';

class ProfileRepository {
  ProfileRepository(this._api);

  final ApiClient _api;

  Future<List<Profile>> list() =>
      _api.get('/profiles', parse: (d) => listOf(d, Profile.fromJson));

  Future<Profile> create({
    required String name,
    required String avatarUrl,
    required bool kids,
    String? maturityRating,
    String? pin,
  }) =>
      _api.post(
        '/profiles',
        body: {
          'name': name.trim(),
          'avatarUrl': avatarUrl,
          'type': kids ? 'KIDS' : 'ADULT',
          'maturityRating': ?maturityRating,
          'pinProtected': pin != null && pin.isNotEmpty,
          if (pin != null && pin.isNotEmpty) 'pin': pin,
        },
        parse: (d) => Profile.fromJson(asJson(d)),
      );

  /// Partial update; only keys present in [changes] are sent.
  Future<Profile> update(String id, Map<String, dynamic> changes) => _api.put(
        '/profiles/$id',
        body: changes,
        parse: (d) => Profile.fromJson(asJson(d)),
      );

  Future<void> delete(String id) => _api.delete('/profiles/$id', parse: ApiClient.ignore);

  Future<SelectProfileResult> select(String id, {String? pin}) => _api.post(
        '/profiles/$id/select',
        query: {if (pin != null && pin.isNotEmpty) 'pin': pin},
        domain401: true,
        parse: (d) => SelectProfileResult.fromJson(asJson(d)),
      );

  Future<List<WatchlistItem>> watchlist() => _api.get(
        '/profiles/me/watchlist',
        parse: (d) => listOf(d, WatchlistItem.fromJson),
      );

  Future<WatchlistItem> addToWatchlist(String titleId, String titleType) => _api.put(
        '/profiles/me/watchlist/$titleId',
        body: {'titleType': titleType},
        parse: (d) => WatchlistItem.fromJson(asJson(d)),
      );

  Future<void> removeFromWatchlist(String titleId) =>
      _api.delete('/profiles/me/watchlist/$titleId', parse: ApiClient.ignore);
}
