import '../../core/api/api_client.dart';
import '../../core/utils/json.dart';
import '../models/catalog_models.dart';

class CatalogRepository {
  CatalogRepository(this._api);

  final ApiClient _api;

  static const lookupBatchSize = 100;

  Future<List<Genre>> genres() =>
      _api.get('/catalog/genres', parse: (d) => listOf(d, Genre.fromJson));

  Map<String, dynamic> _pageQuery({
    String? search,
    String? genreId,
    int page = 0,
    int size = 20,
    String? sort,
  }) =>
      {
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        'genreId': ?genreId,
        'page': page,
        'size': size,
        'sort': ?sort,
      };

  Future<PageResult<CatalogTitle>> movies({
    String? search,
    String? genreId,
    int page = 0,
    int size = 20,
    String? sort,
  }) =>
      _api.get(
        '/catalog/movies',
        query: _pageQuery(search: search, genreId: genreId, page: page, size: size, sort: sort),
        parse: (d) => PageResult.fromJson(asJson(d), CatalogTitle.movieFromJson),
      );

  Future<PageResult<CatalogTitle>> tvShows({
    String? search,
    String? genreId,
    int page = 0,
    int size = 20,
    String? sort,
  }) =>
      _api.get(
        '/catalog/tv-shows',
        query: _pageQuery(search: search, genreId: genreId, page: page, size: size, sort: sort),
        parse: (d) => PageResult.fromJson(asJson(d), CatalogTitle.showFromJson),
      );

  Future<CatalogTitle> movie(String id) =>
      _api.get('/catalog/movies/$id', parse: (d) => CatalogTitle.movieFromJson(asJson(d)));

  Future<TvShowDetail> tvShow(String id) =>
      _api.get('/catalog/tv-shows/$id', parse: (d) => TvShowDetail.fromJson(asJson(d)));

  /// Resolves movie, show and episode ids in batches of 100. Unpublished or
  /// unknown ids are simply absent from the result.
  Future<LookupResult> lookup(Iterable<String> ids) async {
    final unique = ids.toSet().toList();
    if (unique.isEmpty) return LookupResult.empty;
    final batches = <List<String>>[
      for (var i = 0; i < unique.length; i += lookupBatchSize)
        unique.sublist(i, i + lookupBatchSize > unique.length ? unique.length : i + lookupBatchSize),
    ];
    final results = await Future.wait(batches.map((batch) => _api.get(
          '/catalog/lookup',
          query: {'ids': batch.join(',')},
          parse: (d) => d == null ? LookupResult.empty : LookupResult.fromJson(asJson(d)),
        )));
    return results.fold<LookupResult>(LookupResult.empty, (acc, r) => acc.merge(r));
  }
}
