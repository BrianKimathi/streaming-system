import '../../core/utils/json.dart';

class Genre {
  const Genre({required this.id, required this.name, this.slug});

  final String id;
  final String name;
  final String? slug;

  factory Genre.fromJson(Json json) => Genre(
        id: reqStr(json, 'id'),
        name: str(json['name']) ?? '',
        slug: str(json['slug']),
      );
}

enum TitleKind {
  movie,
  series;

  /// Value used by watch history / watchlist (`MOVIE` | `SERIES`).
  String get apiValue => this == movie ? 'MOVIE' : 'SERIES';

  /// Path segment used by in-app routes.
  String get routeValue => this == movie ? 'movie' : 'series';

  static TitleKind fromApi(String? value) {
    final v = (value ?? '').toUpperCase();
    return v == 'SERIES' || v == 'TV_SHOW' || v == 'TVSHOW' || v == 'SHOW'
        ? series
        : movie;
  }
}

/// A movie (`MovieResponse`) or a TV show (`TvShowResponse`).
class CatalogTitle {
  const CatalogTitle({
    required this.id,
    required this.kind,
    required this.title,
    this.synopsis,
    this.releaseDate,
    this.runtimeMinutes,
    this.seasonsCount,
    this.maturityRating,
    this.posterUrl,
    this.backdropUrl,
    this.trailerUrl,
    this.status,
    this.genres = const [],
    this.createdAt,
  });

  final String id;
  final TitleKind kind;
  final String title;
  final String? synopsis;
  final DateTime? releaseDate;
  final int? runtimeMinutes;
  final int? seasonsCount;
  final String? maturityRating;
  final String? posterUrl;
  final String? backdropUrl;
  final String? trailerUrl;
  final String? status;
  final List<Genre> genres;
  final DateTime? createdAt;

  bool get isSeries => kind == TitleKind.series;
  int? get year => releaseDate?.year;

  static String? _url(Object? v) {
    final s = str(v)?.trim();
    return s == null || s.isEmpty ? null : s;
  }

  factory CatalogTitle.movieFromJson(Json json) => CatalogTitle(
        id: reqStr(json, 'id'),
        kind: TitleKind.movie,
        title: str(json['title']) ?? '',
        synopsis: str(json['synopsis']),
        releaseDate: dateOrNull(json['releaseDate']),
        runtimeMinutes: intOrNull(json['runtimeMinutes']),
        maturityRating: str(json['maturityRating']),
        posterUrl: _url(json['posterUrl']),
        backdropUrl: _url(json['backdropUrl']),
        trailerUrl: _url(json['trailerUrl']),
        status: str(json['status']),
        genres: listOf(json['genres'], Genre.fromJson),
        createdAt: dateTimeOrNull(json['createdAt']),
      );

  factory CatalogTitle.showFromJson(Json json) => CatalogTitle(
        id: reqStr(json, 'id'),
        kind: TitleKind.series,
        title: str(json['title']) ?? '',
        synopsis: str(json['synopsis']),
        releaseDate: dateOrNull(json['releaseDate']),
        seasonsCount: intOrNull(json['seasonsCount']),
        maturityRating: str(json['maturityRating']),
        posterUrl: _url(json['posterUrl']),
        backdropUrl: _url(json['backdropUrl']),
        trailerUrl: _url(json['trailerUrl']),
        status: str(json['status']),
        genres: listOf(json['genres'], Genre.fromJson),
        createdAt: dateTimeOrNull(json['createdAt']),
      );
}

class Episode {
  const Episode({
    required this.id,
    required this.title,
    this.seasonId,
    this.tvShowId,
    this.seasonNumber,
    this.episodeNumber,
    this.synopsis,
    this.runtimeMinutes,
    this.releaseDate,
    this.thumbnailUrl,
  });

  final String id;
  final String? seasonId;
  final String? tvShowId;
  final int? seasonNumber;
  final int? episodeNumber;
  final String title;
  final String? synopsis;
  final int? runtimeMinutes;
  final DateTime? releaseDate;
  final String? thumbnailUrl;

  /// "S1:E3"
  String get label => 'S${seasonNumber ?? '?'}:E${episodeNumber ?? '?'}';

  factory Episode.fromJson(Json json) {
    final thumb = str(json['thumbnailUrl'])?.trim();
    return Episode(
      id: reqStr(json, 'id'),
      seasonId: str(json['seasonId']),
      tvShowId: str(json['tvShowId']),
      seasonNumber: intOrNull(json['seasonNumber']),
      episodeNumber: intOrNull(json['episodeNumber']),
      title: str(json['title']) ?? '',
      synopsis: str(json['synopsis']),
      runtimeMinutes: intOrNull(json['runtimeMinutes']),
      releaseDate: dateOrNull(json['releaseDate']),
      thumbnailUrl: thumb == null || thumb.isEmpty ? null : thumb,
    );
  }
}

class Season {
  const Season({
    required this.id,
    required this.seasonNumber,
    this.tvShowId,
    this.title,
    this.synopsis,
    this.releaseDate,
    this.posterUrl,
    this.episodes = const [],
  });

  final String id;
  final String? tvShowId;
  final int seasonNumber;
  final String? title;
  final String? synopsis;
  final DateTime? releaseDate;
  final String? posterUrl;
  final List<Episode> episodes;

  String get displayName {
    final t = title?.trim();
    return t == null || t.isEmpty ? 'Season $seasonNumber' : t;
  }

  factory Season.fromJson(Json json) {
    final number = intOrNull(json['seasonNumber']) ?? 0;
    final episodes = listOf(json['episodes'], (e) {
      final ep = Episode.fromJson(e);
      // Episodes nested in a season may omit the season number.
      return ep.seasonNumber != null
          ? ep
          : Episode(
              id: ep.id,
              seasonId: ep.seasonId,
              tvShowId: ep.tvShowId,
              seasonNumber: number,
              episodeNumber: ep.episodeNumber,
              title: ep.title,
              synopsis: ep.synopsis,
              runtimeMinutes: ep.runtimeMinutes,
              releaseDate: ep.releaseDate,
              thumbnailUrl: ep.thumbnailUrl,
            );
    }).toList()
      ..sort((a, b) => (a.episodeNumber ?? 0).compareTo(b.episodeNumber ?? 0));
    return Season(
      id: reqStr(json, 'id'),
      tvShowId: str(json['tvShowId']),
      seasonNumber: number,
      title: str(json['title']),
      synopsis: str(json['synopsis']),
      releaseDate: dateOrNull(json['releaseDate']),
      posterUrl: str(json['posterUrl']),
      episodes: List.unmodifiable(episodes),
    );
  }
}

/// `TvShowDetailResponse`: show fields plus ordered seasons.
class TvShowDetail {
  const TvShowDetail({required this.show, required this.seasons});

  final CatalogTitle show;
  final List<Season> seasons;

  List<Episode> get allEpisodes => [for (final s in seasons) ...s.episodes];

  Episode? get firstEpisode {
    for (final s in seasons) {
      if (s.episodes.isNotEmpty) return s.episodes.first;
    }
    return null;
  }

  Episode? episodeById(String id) {
    for (final e in allEpisodes) {
      if (e.id == id) return e;
    }
    return null;
  }

  /// The episode after [episodeId] in season/episode order, crossing seasons.
  Episode? nextEpisodeAfter(String episodeId) {
    final all = allEpisodes;
    final i = all.indexWhere((e) => e.id == episodeId);
    if (i < 0 || i + 1 >= all.length) return null;
    return all[i + 1];
  }

  factory TvShowDetail.fromJson(Json json) {
    final seasons = listOf(json['seasons'], Season.fromJson).toList()
      ..sort((a, b) => a.seasonNumber.compareTo(b.seasonNumber));
    return TvShowDetail(
      show: CatalogTitle.showFromJson(json),
      seasons: List.unmodifiable(seasons),
    );
  }
}

/// Spring `Page<T>`.
class PageResult<T> {
  const PageResult({
    required this.content,
    this.totalElements = 0,
    this.totalPages = 0,
    this.number = 0,
    this.size = 0,
    this.last = true,
  });

  final List<T> content;
  final int totalElements;
  final int totalPages;
  final int number;
  final int size;
  final bool last;

  factory PageResult.fromJson(Json json, T Function(Json) parse) {
    final number = intOrNull(json['number']) ?? 0;
    final totalPages = intOrNull(json['totalPages']) ?? 0;
    return PageResult(
      content: listOf(json['content'], parse),
      totalElements: intOrNull(json['totalElements']) ?? 0,
      totalPages: totalPages,
      number: number,
      size: intOrNull(json['size']) ?? 0,
      last: json.containsKey('last') ? boolOr(json['last'], true) : number + 1 >= totalPages,
    );
  }
}

/// `GET /catalog/lookup` result.
class LookupResult {
  const LookupResult({
    this.movies = const [],
    this.tvShows = const [],
    this.episodes = const [],
  });

  final List<CatalogTitle> movies;
  final List<CatalogTitle> tvShows;
  final List<Episode> episodes;

  static const empty = LookupResult();

  Map<String, CatalogTitle> get titlesById => {
        for (final t in movies) t.id: t,
        for (final t in tvShows) t.id: t,
      };

  Map<String, Episode> get episodesById => {for (final e in episodes) e.id: e};

  LookupResult merge(LookupResult other) => LookupResult(
        movies: [...movies, ...other.movies],
        tvShows: [...tvShows, ...other.tvShows],
        episodes: [...episodes, ...other.episodes],
      );

  factory LookupResult.fromJson(Json json) => LookupResult(
        movies: listOf(json['movies'], CatalogTitle.movieFromJson),
        tvShows: listOf(json['tvShows'], CatalogTitle.showFromJson),
        episodes: listOf(json['episodes'], Episode.fromJson),
      );
}
