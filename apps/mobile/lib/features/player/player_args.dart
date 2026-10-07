import '../../data/models/catalog_models.dart';

/// What to play. For a movie `contentId == titleId`; for a series
/// `contentId` is the episode id and `titleId` the show id.
class PlayerArgs {
  const PlayerArgs({
    required this.contentId,
    required this.titleId,
    required this.kind,
    required this.title,
    this.episode,
    this.fromBeginning = false,
    this.trailerUrl,
    this.maturityRating,
  });

  /// Plays a trailer URL directly (no playback session / progress).
  const PlayerArgs.trailer({required this.title, required String url})
      : contentId = '',
        titleId = '',
        kind = TitleKind.movie,
        episode = null,
        fromBeginning = true,
        trailerUrl = url,
        maturityRating = null;

  factory PlayerArgs.movie(CatalogTitle movie, {bool fromBeginning = false}) => PlayerArgs(
        contentId: movie.id,
        titleId: movie.id,
        kind: TitleKind.movie,
        title: movie.title,
        fromBeginning: fromBeginning,
        maturityRating: movie.maturityRating,
      );

  factory PlayerArgs.episode({
    required CatalogTitle show,
    required Episode episode,
    bool fromBeginning = false,
  }) =>
      PlayerArgs(
        contentId: episode.id,
        titleId: show.id,
        kind: TitleKind.series,
        title: show.title,
        episode: episode,
        fromBeginning: fromBeginning,
        maturityRating: show.maturityRating,
      );

  PlayerArgs restart() => PlayerArgs(
        contentId: contentId,
        titleId: titleId,
        kind: kind,
        title: title,
        episode: episode,
        fromBeginning: true,
        trailerUrl: trailerUrl,
        maturityRating: maturityRating,
      );

  final String contentId;
  final String titleId;
  final TitleKind kind;
  final String title;
  final Episode? episode;

  /// Ignore saved progress and start at 0.
  final bool fromBeginning;
  final String? trailerUrl;

  /// Rating of the movie/show, checked against the profile before playing.
  final String? maturityRating;

  bool get isTrailer => trailerUrl != null;
}
