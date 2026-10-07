import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../data/content_filter.dart';
import '../../data/models/catalog_models.dart';

class SearchState {
  const SearchState({
    this.query = '',
    this.genreId,
    this.items = const [],
    this.loading = false,
    this.loadingMore = false,
    this.error,
    this.moviesPage = -1,
    this.showsPage = -1,
    this.moviesLast = false,
    this.showsLast = false,
  });

  final String query;
  final String? genreId;
  final List<CatalogTitle> items;
  final bool loading;
  final bool loadingMore;
  final String? error;
  final int moviesPage;
  final int showsPage;
  final bool moviesLast;
  final bool showsLast;

  bool get hasMore => !moviesLast || !showsLast;
  bool get started => moviesPage >= 0 || showsPage >= 0;

  SearchState copyWith({
    String? query,
    String? genreId,
    bool clearGenre = false,
    List<CatalogTitle>? items,
    bool? loading,
    bool? loadingMore,
    String? error,
    bool clearError = false,
    int? moviesPage,
    int? showsPage,
    bool? moviesLast,
    bool? showsLast,
  }) =>
      SearchState(
        query: query ?? this.query,
        genreId: clearGenre ? null : (genreId ?? this.genreId),
        items: items ?? this.items,
        loading: loading ?? this.loading,
        loadingMore: loadingMore ?? this.loadingMore,
        error: clearError ? null : (error ?? this.error),
        moviesPage: moviesPage ?? this.moviesPage,
        showsPage: showsPage ?? this.showsPage,
        moviesLast: moviesLast ?? this.moviesLast,
        showsLast: showsLast ?? this.showsLast,
      );
}

final searchControllerProvider =
    NotifierProvider.autoDispose<CatalogSearchController, SearchState>(CatalogSearchController.new);

/// Searches movies and TV shows in parallel and pages through both until
/// each reports `last`.
class CatalogSearchController extends Notifier<SearchState> {
  static const pageSize = 20;
  int _generation = 0;

  @override
  SearchState build() {
    ref.watch(profileKeyProvider);
    Future.microtask(_reload);
    return const SearchState(loading: true);
  }

  void setQuery(String query) {
    if (query.trim() == state.query.trim()) return;
    state = state.copyWith(query: query.trim());
    _reload();
  }

  void setGenre(String? genreId) {
    if (genreId == state.genreId) return;
    state = genreId == null ? state.copyWith(clearGenre: true) : state.copyWith(genreId: genreId);
    _reload();
  }

  Future<void> refresh() => _reload();

  Future<void> _reload() async {
    final generation = ++_generation;
    state = SearchState(query: state.query, genreId: state.genreId, loading: true);
    await _fetchNext(generation, initial: true);
  }

  Future<void> loadMore() async {
    if (state.loading || state.loadingMore || !state.hasMore || state.error != null) return;
    state = state.copyWith(loadingMore: true);
    await _fetchNext(_generation, initial: false);
  }

  Future<void> _fetchNext(int generation, {required bool initial}) async {
    final catalog = ref.read(catalogRepositoryProvider);
    final s = state;
    try {
      final moviesFuture = s.moviesLast
          ? null
          : catalog.movies(
              search: s.query, genreId: s.genreId, page: s.moviesPage + 1, size: pageSize);
      final showsFuture = s.showsLast
          ? null
          : catalog.tvShows(
              search: s.query, genreId: s.genreId, page: s.showsPage + 1, size: pageSize);
      final movies = await moviesFuture;
      final shows = await showsFuture;
      if (!ref.mounted || generation != _generation) return;

      final fresh = <CatalogTitle>[];
      final m = movies?.content ?? const <CatalogTitle>[];
      final t = shows?.content ?? const <CatalogTitle>[];
      for (var i = 0; i < m.length || i < t.length; i++) {
        if (i < m.length) fresh.add(m[i]);
        if (i < t.length) fresh.add(t[i]);
      }
      final profile = ref.read(currentProfileProvider);
      final seen = state.items.map((e) => e.id).toSet();
      state = state.copyWith(
        items: [
          ...state.items,
          ...filterForProfile(fresh, profile).where((e) => seen.add(e.id)),
        ],
        loading: false,
        loadingMore: false,
        clearError: true,
        moviesPage: movies != null ? s.moviesPage + 1 : s.moviesPage,
        showsPage: shows != null ? s.showsPage + 1 : s.showsPage,
        moviesLast: movies?.last ?? s.moviesLast,
        showsLast: shows?.last ?? s.showsLast,
      );
    } on ApiException catch (e) {
      if (!ref.mounted || generation != _generation) return;
      state = state.copyWith(loading: false, loadingMore: false, error: e.message);
    }
  }

  void clearErrorAndRetry() {
    if (state.items.isEmpty) {
      _reload();
    } else {
      state = state.copyWith(clearError: true);
      loadMore();
    }
  }
}
