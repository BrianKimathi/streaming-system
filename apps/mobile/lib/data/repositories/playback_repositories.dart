import '../../core/api/api_client.dart';
import '../../core/api/api_exception.dart';
import '../../core/utils/json.dart';
import '../models/misc_models.dart';

class PlaybackRepository {
  PlaybackRepository(this._api);

  final ApiClient _api;

  Future<PlaybackGrant> request({
    required String contentId,
    required String deviceId,
    String? titleId,
  }) =>
      _api.post(
        '/playback/request',
        body: {'contentId': contentId, 'deviceId': deviceId, 'titleId': ?titleId},
        parse: (d) => PlaybackGrant.fromJson(asJson(d)),
      );

  Future<void> heartbeat(String sessionId) =>
      _api.post('/playback/sessions/$sessionId/heartbeat', parse: ApiClient.ignore);

  Future<void> stop(String sessionId) =>
      _api.post('/playback/sessions/$sessionId/stop', parse: ApiClient.ignore);
}

class WatchHistoryRepository {
  WatchHistoryRepository(this._api);

  final ApiClient _api;

  Future<WatchProgress> saveProgress({
    required String contentId,
    required String titleId,
    required String titleType,
    required int positionSeconds,
    required int durationSeconds,
  }) =>
      _api.post(
        '/watch-history/progress',
        body: {
          'contentId': contentId,
          'titleId': titleId,
          'titleType': titleType,
          'positionSeconds': positionSeconds,
          'durationSeconds': durationSeconds,
        },
        parse: (d) => WatchProgress.fromJson(asJson(d)),
      );

  Future<List<WatchProgress>> continueWatching() => _api.get(
        '/watch-history/continue-watching',
        parse: (d) => listOf(d, WatchProgress.fromJson),
      );

  Future<List<WatchProgress>> forTitle(String titleId) => _api.get(
        '/watch-history/titles/$titleId',
        parse: (d) => listOf(d, WatchProgress.fromJson),
      );

  /// Saved progress for a movie or episode id, or null if never watched.
  Future<WatchProgress?> forContent(String contentId) async {
    try {
      return await _api.get(
        '/watch-history/$contentId',
        parse: (d) => d == null ? null : WatchProgress.fromJson(asJson(d)),
      );
    } on ApiException catch (e) {
      if (e.isNotFound) return null;
      rethrow;
    }
  }

  Future<void> removeTitle(String titleId) =>
      _api.delete('/watch-history/titles/$titleId', parse: ApiClient.ignore);
}

class TrendingRepository {
  TrendingRepository(this._api);

  final ApiClient _api;

  Future<List<TrendingItem>> trending({int limit = 20}) => _api.get(
        '/trending',
        query: {'limit': limit},
        parse: (d) => listOf(d, TrendingItem.fromJson),
      );
}
