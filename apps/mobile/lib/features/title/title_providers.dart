import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../data/models/catalog_models.dart';
import '../../data/models/misc_models.dart';
import '../../data/models/profile_models.dart';

typedef TitleRef = ({TitleKind kind, String id});

class TitleDetail {
  const TitleDetail({required this.title, this.show});

  final CatalogTitle title;

  /// Seasons and episodes, for series only.
  final TvShowDetail? show;
}

final titleDetailProvider =
    FutureProvider.autoDispose.family<TitleDetail, TitleRef>((ref, t) async {
  final catalog = ref.watch(catalogRepositoryProvider);
  if (t.kind == TitleKind.movie) {
    return TitleDetail(title: await catalog.movie(t.id));
  }
  final show = await catalog.tvShow(t.id);
  return TitleDetail(title: show.show, show: show);
});

/// Saved progress for the title: one row for a movie, one per watched
/// episode for a series.
final titleProgressProvider =
    FutureProvider.autoDispose.family<List<WatchProgress>, TitleRef>((ref, t) async {
  ref.watch(profileKeyProvider);
  final history = ref.watch(watchHistoryRepositoryProvider);
  if (t.kind == TitleKind.movie) {
    final p = await history.forContent(t.id);
    return p == null ? const [] : [p];
  }
  final rows = await history.forTitle(t.id);
  return [...rows]..sort((a, b) {
      final at = a.lastWatchedAt?.millisecondsSinceEpoch ?? 0;
      final bt = b.lastWatchedAt?.millisecondsSinceEpoch ?? 0;
      return bt.compareTo(at);
    });
});

final watchlistProvider = FutureProvider.autoDispose<List<WatchlistItem>>((ref) {
  ref.watch(profileKeyProvider);
  return ref.watch(profileRepositoryProvider).watchlist();
});

/// Series resume target: the most recently watched episode, or the next one
/// if that episode was finished, or the first episode if nothing was watched.
class SeriesResume {
  const SeriesResume({required this.episode, this.progress, required this.isResume});

  final Episode episode;
  final WatchProgress? progress;
  final bool isResume;
}

SeriesResume? seriesResumeTarget(TvShowDetail show, List<WatchProgress> progressNewestFirst) {
  for (final row in progressNewestFirst) {
    final episode = show.episodeById(row.contentId);
    if (episode == null) continue;
    if (!row.completed) {
      return SeriesResume(episode: episode, progress: row, isResume: row.positionSeconds > 0);
    }
    final next = show.nextEpisodeAfter(episode.id);
    if (next != null) {
      final nextProgress = progressNewestFirst.where((p) => p.contentId == next.id).firstOrNull;
      return SeriesResume(
        episode: next,
        progress: nextProgress,
        isResume: nextProgress != null && !nextProgress.completed && nextProgress.positionSeconds > 0,
      );
    }
    return SeriesResume(episode: episode, progress: row, isResume: false);
  }
  final first = show.firstEpisode;
  return first == null ? null : SeriesResume(episode: first, isResume: false);
}
