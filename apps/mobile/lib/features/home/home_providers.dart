import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../data/content_filter.dart';
import '../../data/models/catalog_models.dart';
import '../../data/models/misc_models.dart';

class ContinueWatchingItem {
  const ContinueWatchingItem({required this.progress, required this.title, this.episode});

  final WatchProgress progress;
  final CatalogTitle title;
  final Episode? episode;
}

class GenreRow {
  const GenreRow(this.genre, this.titles);

  final Genre genre;
  final List<CatalogTitle> titles;
}

const _rowSize = 20;

/// Trending titles resolved through the catalog lookup (unpublished titles
/// are absent from the lookup and therefore skipped), in trending order.
final trendingTitlesProvider = FutureProvider.autoDispose<List<CatalogTitle>>((ref) async {
  ref.watch(profileKeyProvider);
  final profile = ref.read(currentProfileProvider);
  final items = await ref.watch(trendingRepositoryProvider).trending(limit: 20);
  if (items.isEmpty) return const [];
  final lookup = await ref.watch(catalogRepositoryProvider).lookup(items.map((e) => e.contentId));
  final byId = lookup.titlesById;
  final ordered = [
    for (final item in items)
      if (byId[item.contentId] != null) byId[item.contentId]!,
  ];
  return filterForProfile(ordered, profile);
});

final newMoviesProvider = FutureProvider.autoDispose<List<CatalogTitle>>((ref) async {
  ref.watch(profileKeyProvider);
  final profile = ref.read(currentProfileProvider);
  final page = await ref
      .watch(catalogRepositoryProvider)
      .movies(size: _rowSize, sort: 'releaseDate,desc');
  return filterForProfile(page.content, profile);
});

final tvShowsProvider = FutureProvider.autoDispose<List<CatalogTitle>>((ref) async {
  ref.watch(profileKeyProvider);
  final profile = ref.read(currentProfileProvider);
  final page = await ref
      .watch(catalogRepositoryProvider)
      .tvShows(size: _rowSize, sort: 'releaseDate,desc');
  return filterForProfile(page.content, profile);
});

final genresProvider = FutureProvider.autoDispose<List<Genre>>(
  (ref) => ref.watch(catalogRepositoryProvider).genres(),
);

/// One row per genre that has visible content; empty genres are dropped.
final genreRowsProvider = FutureProvider.autoDispose<List<GenreRow>>((ref) async {
  ref.watch(profileKeyProvider);
  final profile = ref.read(currentProfileProvider);
  final catalog = ref.watch(catalogRepositoryProvider);
  final genres = await ref.watch(genresProvider.future);
  final rows = await Future.wait(genres.map((g) async {
    final results = await Future.wait([
      catalog.movies(genreId: g.id, size: _rowSize, sort: 'releaseDate,desc'),
      catalog.tvShows(genreId: g.id, size: _rowSize, sort: 'releaseDate,desc'),
    ]);
    final merged = <CatalogTitle>[];
    final movies = results[0].content;
    final shows = results[1].content;
    for (var i = 0; i < movies.length || i < shows.length; i++) {
      if (i < movies.length) merged.add(movies[i]);
      if (i < shows.length) merged.add(shows[i]);
    }
    return GenreRow(g, filterForProfile(merged, profile));
  }));
  return rows.where((r) => r.titles.isNotEmpty).toList(growable: false);
});

final continueWatchingProvider =
    FutureProvider.autoDispose<List<ContinueWatchingItem>>((ref) async {
  ref.watch(profileKeyProvider);
  final profile = ref.read(currentProfileProvider);
  if (profile == null) return const [];
  final entries = await ref.watch(watchHistoryRepositoryProvider).continueWatching();
  if (entries.isEmpty) return const [];
  final ids = <String>{
    for (final e in entries) ...[e.titleId ?? e.contentId, e.contentId],
  };
  final lookup = await ref.watch(catalogRepositoryProvider).lookup(ids);
  final titles = lookup.titlesById;
  final episodes = lookup.episodesById;
  final items = <ContinueWatchingItem>[];
  for (final e in entries) {
    final title = titles[e.titleId ?? e.contentId];
    if (title == null || !titleAllowed(title, profile)) continue;
    final episode = title.isSeries ? episodes[e.contentId] : null;
    if (title.isSeries && episode == null) continue;
    items.add(ContinueWatchingItem(progress: e, title: title, episode: episode));
  }
  return items;
});
