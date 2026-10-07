import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../core/utils/maturity.dart';
import '../../data/content_filter.dart';
import '../../data/models/catalog_models.dart';
import '../../data/models/misc_models.dart';
import '../../widgets/artwork.dart';
import '../../widgets/state_views.dart';
import '../player/player_args.dart';
import 'title_providers.dart';

class TitleDetailsScreen extends ConsumerWidget {
  const TitleDetailsScreen({super.key, required this.kind, required this.id});

  final String kind;
  final String id;

  TitleRef get _ref => (kind: kind == 'series' ? TitleKind.series : TitleKind.movie, id: id);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(titleDetailProvider(_ref));
    return Scaffold(
      body: detail.when(
        loading: () => Scaffold(appBar: AppBar(), body: const LoadingView()),
        error: (e, _) => Scaffold(
          appBar: AppBar(),
          body: ErrorView(
            error: e is ApiException && e.isNotFound
                ? 'This title is not available.'
                : e,
            onRetry: () => ref.invalidate(titleDetailProvider(_ref)),
          ),
        ),
        data: (d) => _DetailBody(titleRef: _ref, detail: d),
      ),
    );
  }
}

class _DetailBody extends ConsumerStatefulWidget {
  const _DetailBody({required this.titleRef, required this.detail});

  final TitleRef titleRef;
  final TitleDetail detail;

  @override
  ConsumerState<_DetailBody> createState() => _DetailBodyState();
}

class _DetailBodyState extends ConsumerState<_DetailBody> {
  int? _userSeason;
  bool _listBusy = false;

  CatalogTitle get _title => widget.detail.title;

  Future<void> _play(PlayerArgs args) async {
    await context.push('/play', extra: args);
    if (!mounted) return;
    ref.invalidate(titleProgressProvider(widget.titleRef));
  }

  Future<void> _toggleList(bool inList) async {
    setState(() => _listBusy = true);
    final repo = ref.read(profileRepositoryProvider);
    try {
      if (inList) {
        await repo.removeFromWatchlist(_title.id);
      } else {
        await repo.addToWatchlist(_title.id, _title.kind.apiValue);
      }
      ref.invalidate(watchlistProvider);
      await ref.read(watchlistProvider.future);
      if (mounted) showMessage(context, inList ? 'Removed from My List.' : 'Added to My List.');
    } on ApiException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _listBusy = false);
    }
  }

  Future<void> _openTrailer(String url) async {
    final path = Uri.parse(url).path.toLowerCase();
    if (path.endsWith('.m3u8') || path.endsWith('.mp4')) {
      context.push('/play', extra: PlayerArgs.trailer(title: '${_title.title} – Trailer', url: url));
      return;
    }
    final ok = await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
    if (!ok && mounted) showMessage(context, "The trailer couldn't be opened.");
  }

  @override
  Widget build(BuildContext context) {
    final profile = ref.watch(currentProfileProvider);
    final allowed = titleAllowed(_title, profile);
    final progress = ref.watch(titleProgressProvider(widget.titleRef));
    final watchlist = ref.watch(watchlistProvider);
    final show = widget.detail.show;

    final meta = [
      if (_title.year != null) '${_title.year}',
      if (_title.maturityRating != null && _title.maturityRating!.isNotEmpty)
        displayRating(_title.maturityRating!),
      if (_title.isSeries)
        '${show?.seasons.length ?? _title.seasonsCount ?? 0} season${(show?.seasons.length ?? _title.seasonsCount) == 1 ? '' : 's'}'
      else if (formatRuntime(_title.runtimeMinutes).isNotEmpty)
        formatRuntime(_title.runtimeMinutes),
    ];

    final trailer = _title.trailerUrl;
    final hasTrailer = TitleArtwork.isHttpUrl(trailer);

    final seasons = show?.seasons ?? const <Season>[];
    final seasonIndex = (_userSeason != null && _userSeason! < seasons.length)
        ? _userSeason!
        : _initialSeason(show, progress.value);

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          pinned: true,
          expandedHeight: MediaQuery.sizeOf(context).width * 9 / 16,
          flexibleSpace: FlexibleSpaceBar(
            background: Stack(
              fit: StackFit.expand,
              children: [
                TitleArtwork(
                  title: _title.title,
                  imageUrl: _title.backdropUrl ?? _title.posterUrl,
                  backdrop: true,
                  borderRadius: 0,
                ),
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0x66000000), Colors.transparent, AppColors.background],
                      stops: [0, 0.4, 1],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          sliver: SliverList.list(
            children: [
              Text(_title.title, style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 8),
              Wrap(
                spacing: 10,
                runSpacing: 6,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  for (final m in meta)
                    Text(m, style: const TextStyle(color: AppColors.textSecondary)),
                ],
              ),
              const SizedBox(height: 16),
              if (!allowed)
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.lock_outline_rounded, color: AppColors.textSecondary),
                      SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          "This title is above this profile's maturity rating.",
                          style: TextStyle(color: AppColors.textSecondary),
                        ),
                      ),
                    ],
                  ),
                )
              else
                _PlaySection(
                  title: _title,
                  show: show,
                  progress: progress,
                  onPlay: _play,
                  onRetry: () => ref.invalidate(titleProgressProvider(widget.titleRef)),
                ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: watchlist.when(
                      loading: () => const OutlinedButton(
                        onPressed: null,
                        child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
                      ),
                      error: (e, _) => OutlinedButton.icon(
                        onPressed: () => ref.invalidate(watchlistProvider),
                        icon: const Icon(Icons.refresh_rounded),
                        label: const Text('My List'),
                      ),
                      data: (items) {
                        final inList = items.any((i) => i.titleId == _title.id);
                        return OutlinedButton.icon(
                          onPressed: _listBusy ? null : () => _toggleList(inList),
                          icon: _listBusy
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                              : Icon(inList ? Icons.check_rounded : Icons.add_rounded),
                          label: Text(inList ? 'In My List' : 'My List'),
                        );
                      },
                    ),
                  ),
                  if (hasTrailer) ...[
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => _openTrailer(trailer!.trim()),
                        icon: const Icon(Icons.movie_outlined),
                        label: const Text('Trailer'),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 18),
              if (_title.synopsis != null && _title.synopsis!.trim().isNotEmpty)
                Text(_title.synopsis!.trim(), style: const TextStyle(height: 1.5)),
              if (_title.genres.isNotEmpty) ...[
                const SizedBox(height: 14),
                Text(
                  'Genres: ${_title.genres.map((g) => g.name).join(', ')}',
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                ),
              ],
              if (show != null) ...[
                const SizedBox(height: 22),
                if (seasons.isEmpty)
                  const Text('No episodes have been published yet.',
                      style: TextStyle(color: AppColors.textSecondary))
                else
                  Row(
                    children: [
                      const Text('Episodes',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                      const Spacer(),
                      if (seasons.length > 1)
                        DropdownButton<int>(
                          value: seasonIndex,
                          underline: const SizedBox.shrink(),
                          dropdownColor: AppColors.surfaceHigh,
                          items: [
                            for (var i = 0; i < seasons.length; i++)
                              DropdownMenuItem(value: i, child: Text(seasons[i].displayName)),
                          ],
                          onChanged: (i) => setState(() => _userSeason = i ?? 0),
                        )
                      else
                        Text(seasons.first.displayName,
                            style: const TextStyle(color: AppColors.textSecondary)),
                    ],
                  ),
              ],
            ],
          ),
        ),
        if (show != null && seasons.isNotEmpty)
          _EpisodeList(
            show: show,
            season: seasons[seasonIndex],
            progress: progress.value ?? const [],
            enabled: allowed,
            onPlay: _play,
          ),
        const SliverToBoxAdapter(child: SizedBox(height: 32)),
      ],
    );
  }

  int _initialSeason(TvShowDetail? show, List<WatchProgress>? progress) {
    if (show == null || show.seasons.isEmpty) return 0;
    final target = progress == null ? null : seriesResumeTarget(show, progress);
    if (target != null) {
      final i = show.seasons.indexWhere((s) => s.episodes.any((e) => e.id == target.episode.id));
      if (i >= 0) return i;
    }
    return 0;
  }
}

class _PlaySection extends StatelessWidget {
  const _PlaySection({
    required this.title,
    required this.show,
    required this.progress,
    required this.onPlay,
    required this.onRetry,
  });

  final CatalogTitle title;
  final TvShowDetail? show;
  final AsyncValue<List<WatchProgress>> progress;
  final void Function(PlayerArgs) onPlay;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (progress.isLoading && !progress.hasValue) {
      return const FilledButton(
        onPressed: null,
        child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
      );
    }
    final rows = progress.value ?? const <WatchProgress>[];
    final progressError = progress.hasError ? progress.error : null;

    PlayerArgs? args;
    String label;
    WatchProgress? current;
    bool resume = false;

    if (show == null) {
      current = rows.firstOrNull;
      resume = current != null && !current.completed && current.positionSeconds > 0;
      args = PlayerArgs.movie(title);
      label = resume ? 'Resume' : 'Play';
    } else {
      final target = seriesResumeTarget(show!, rows);
      if (target == null) {
        args = null;
        label = 'No episodes yet';
      } else {
        current = target.progress;
        resume = target.isResume;
        args = PlayerArgs.episode(show: title, episode: target.episode);
        label = '${resume ? 'Resume' : 'Play'} ${target.episode.label}';
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton.icon(
          onPressed: args == null ? null : () => onPlay(args!),
          icon: const Icon(Icons.play_arrow_rounded),
          label: Text(label),
        ),
        if (resume && current != null) ...[
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(2),
                  child: LinearProgressIndicator(
                    value: current.fraction,
                    minHeight: 3,
                    backgroundColor: AppColors.surfaceHigh,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Text(
                current.durationSeconds > 0
                    ? '${formatClock(Duration(seconds: current.durationSeconds - current.positionSeconds))} left'
                    : '',
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
              ),
            ],
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: args == null
                  ? null
                  : () => onPlay(args!.restart()),
              icon: const Icon(Icons.replay_rounded, size: 18),
              label: const Text('Play from beginning'),
            ),
          ),
        ],
        if (progressError != null)
          Row(
            children: [
              Expanded(
                child: Text(
                  "Your progress couldn't be loaded: ${errorMessage(progressError)}",
                  style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                ),
              ),
              TextButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
      ],
    );
  }
}

class _EpisodeList extends StatelessWidget {
  const _EpisodeList({
    required this.show,
    required this.season,
    required this.progress,
    required this.enabled,
    required this.onPlay,
  });

  final TvShowDetail show;
  final Season season;
  final List<WatchProgress> progress;
  final bool enabled;
  final void Function(PlayerArgs) onPlay;

  @override
  Widget build(BuildContext context) {
    if (season.episodes.isEmpty) {
      return const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text('No episodes in this season yet.',
              style: TextStyle(color: AppColors.textSecondary)),
        ),
      );
    }
    final byContent = {for (final p in progress) p.contentId: p};
    return SliverList.separated(
      itemCount: season.episodes.length,
      separatorBuilder: (_, _) => const SizedBox(height: 4),
      itemBuilder: (context, i) {
        final ep = season.episodes[i];
        final p = byContent[ep.id];
        return InkWell(
          onTap: !enabled
              ? null
              : () => onPlay(PlayerArgs.episode(show: show.show, episode: ep)),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    SizedBox(
                      width: 132,
                      child: AspectRatio(
                        aspectRatio: 16 / 9,
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            TitleArtwork(
                              title: ep.title.isEmpty ? ep.label : ep.title,
                              imageUrl: ep.thumbnailUrl,
                              backdrop: false,
                            ),
                            const Center(
                              child: Icon(Icons.play_circle_outline_rounded, color: Colors.white70, size: 34),
                            ),
                            if (p != null)
                              Positioned(
                                left: 0,
                                right: 0,
                                bottom: 0,
                                child: LinearProgressIndicator(
                                  value: p.completed ? 1 : p.fraction,
                                  minHeight: 3,
                                  backgroundColor: Colors.black54,
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${ep.episodeNumber ?? i + 1}. ${ep.title}',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            [
                              if (formatRuntime(ep.runtimeMinutes).isNotEmpty) formatRuntime(ep.runtimeMinutes),
                              if (p?.completed ?? false) 'Watched',
                            ].join(' · '),
                            style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (ep.synopsis != null && ep.synopsis!.trim().isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(
                    ep.synopsis!.trim(),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.4),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
