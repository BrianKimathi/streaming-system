import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../core/utils/maturity.dart';
import '../../data/models/catalog_models.dart';
import '../../widgets/artwork.dart';
import '../../widgets/poster_card.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/state_views.dart';
import '../player/player_args.dart';
import '../splash/splash_screen.dart';
import 'home_providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(trendingTitlesProvider);
    ref.invalidate(continueWatchingProvider);
    ref.invalidate(newMoviesProvider);
    ref.invalidate(tvShowsProvider);
    ref.invalidate(genresProvider);
    ref.invalidate(genreRowsProvider);
    await Future.wait<Object?>([
      ref.read(trendingTitlesProvider.future),
      ref.read(continueWatchingProvider.future),
      ref.read(newMoviesProvider.future),
      ref.read(tvShowsProvider.future),
    ].map((f) => f.then<Object?>((v) => v, onError: (_) => null)));
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(currentProfileProvider);
    final trending = ref.watch(trendingTitlesProvider);
    final movies = ref.watch(newMoviesProvider);
    final shows = ref.watch(tvShowsProvider);
    final continueWatching = ref.watch(continueWatchingProvider);
    final genreRows = ref.watch(genreRowsProvider);

    final catalogEmpty = (trending.value?.isEmpty ?? false) &&
        (movies.value?.isEmpty ?? false) &&
        (shows.value?.isEmpty ?? false) &&
        !trending.isLoading &&
        !movies.isLoading &&
        !shows.isLoading;

    final hero = trending.value?.firstOrNull ??
        (trending.hasValue ? (movies.value?.firstOrNull ?? shows.value?.firstOrNull) : null);

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () => _refresh(ref),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverAppBar(
              floating: true,
              snap: true,
              backgroundColor: AppColors.background.withValues(alpha: 0.95),
              title: const StreamXLogo(size: 22),
              actions: [
                if (profile != null)
                  Padding(
                    padding: const EdgeInsets.only(right: 12),
                    child: InkWell(
                      onTap: () => context.go('/account'),
                      child: ProfileAvatar(
                        avatarUrl: profile.avatarUrl,
                        name: profile.name,
                        size: 32,
                      ),
                    ),
                  ),
              ],
            ),
            if (catalogEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: EmptyView(
                  icon: Icons.movie_filter_outlined,
                  title: 'No titles have been published yet',
                  message: profile?.isKids ?? false
                      ? 'Titles suitable for this profile will appear here once they are published.'
                      : 'New movies and shows will appear here as soon as they are published.',
                ),
              )
            else ...[
              SliverToBoxAdapter(
                child: hero != null
                    ? _Hero(title: hero)
                    : (trending.isLoading || movies.isLoading)
                        ? const AspectRatio(aspectRatio: 16 / 9, child: SkeletonBox(radius: 0))
                        : const SizedBox.shrink(),
              ),
              SliverToBoxAdapter(
                child: continueWatching.when(
                  loading: () => const SizedBox.shrink(),
                  error: (e, _) => _RowError(
                    title: 'Continue Watching',
                    error: e,
                    onRetry: () => ref.invalidate(continueWatchingProvider),
                  ),
                  data: (items) => items.isEmpty
                      ? const SizedBox.shrink()
                      : _ContinueWatchingRow(items: items),
                ),
              ),
              SliverToBoxAdapter(
                child: _AsyncRow(
                  title: 'Trending Now',
                  value: trending,
                  onRetry: () => ref.invalidate(trendingTitlesProvider),
                ),
              ),
              SliverToBoxAdapter(
                child: _AsyncRow(
                  title: 'New Movies',
                  value: movies,
                  onRetry: () => ref.invalidate(newMoviesProvider),
                ),
              ),
              SliverToBoxAdapter(
                child: _AsyncRow(
                  title: 'TV Shows',
                  value: shows,
                  onRetry: () => ref.invalidate(tvShowsProvider),
                ),
              ),
              ...genreRows.when(
                loading: () => [const SliverToBoxAdapter(child: PosterRowSkeleton())],
                error: (e, _) => [
                  SliverToBoxAdapter(
                    child: _RowError(
                      title: 'Genres',
                      error: e,
                      onRetry: () {
                        ref.invalidate(genresProvider);
                        ref.invalidate(genreRowsProvider);
                      },
                    ),
                  ),
                ],
                data: (rows) => [
                  for (final row in rows)
                    SliverToBoxAdapter(child: PosterRow(title: row.genre.name, items: row.titles)),
                ],
              ),
              const SliverToBoxAdapter(child: SizedBox(height: 32)),
            ],
          ],
        ),
      ),
    );
  }
}

class _AsyncRow extends StatelessWidget {
  const _AsyncRow({required this.title, required this.value, required this.onRetry});

  final String title;
  final AsyncValue<List<CatalogTitle>> value;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return value.when(
      loading: () => const PosterRowSkeleton(),
      error: (e, _) => _RowError(title: title, error: e, onRetry: onRetry),
      data: (items) => items.isEmpty ? const SizedBox.shrink() : PosterRow(title: title, items: items),
    );
  }
}

class _RowError extends StatelessWidget {
  const _RowError({required this.title, required this.error, required this.onRetry});

  final String title;
  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(title),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    errorMessage(error),
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                  ),
                ),
                TextButton(onPressed: onRetry, child: const Text('Retry')),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _Hero extends StatelessWidget {
  const _Hero({required this.title});

  final CatalogTitle title;

  @override
  Widget build(BuildContext context) {
    final meta = [
      if (title.year != null) '${title.year}',
      if (title.maturityRating != null && title.maturityRating!.isNotEmpty)
        displayRating(title.maturityRating!),
      if (title.isSeries)
        title.seasonsCount == null
            ? 'Series'
            : '${title.seasonsCount} season${title.seasonsCount == 1 ? '' : 's'}'
      else if (formatRuntime(title.runtimeMinutes).isNotEmpty)
        formatRuntime(title.runtimeMinutes),
      ...title.genres.take(2).map((g) => g.name),
    ].join('  •  ');
    return AspectRatio(
      aspectRatio: 16 / 11,
      child: Stack(
        fit: StackFit.expand,
        children: [
          TitleArtwork(
            title: title.title,
            imageUrl: title.backdropUrl ?? title.posterUrl,
            backdrop: true,
            borderRadius: 0,
          ),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.transparent, Color(0x990B0F19), AppColors.background],
                stops: [0.35, 0.7, 1],
              ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 12,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    shadows: const [Shadow(blurRadius: 12, color: Colors.black)],
                  ),
                ),
                if (meta.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  Text(meta, style: const TextStyle(color: AppColors.textSecondary)),
                ],
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () => title.isSeries
                            ? openTitle(context, title)
                            : context.push('/play', extra: PlayerArgs.movie(title)),
                        icon: const Icon(Icons.play_arrow_rounded),
                        label: Text(title.isSeries ? 'Episodes' : 'Play'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => openTitle(context, title),
                        style: OutlinedButton.styleFrom(backgroundColor: Colors.black38),
                        icon: const Icon(Icons.info_outline_rounded),
                        label: const Text('Details'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ContinueWatchingRow extends ConsumerWidget {
  const _ContinueWatchingRow({required this.items});

  final List<ContinueWatchingItem> items;

  Future<void> _remove(BuildContext context, WidgetRef ref, ContinueWatchingItem item) async {
    final ok = await confirm(
      context,
      title: 'Remove from Continue Watching?',
      message: '"${item.title.title}" and its watch progress will be removed from this row.',
      confirmLabel: 'Remove',
      destructive: true,
    );
    if (!ok || !context.mounted) return;
    try {
      await ref.read(watchHistoryRepositoryProvider).removeTitle(item.title.id);
      ref.invalidate(continueWatchingProvider);
      if (context.mounted) showMessage(context, 'Removed from Continue Watching.');
    } on ApiException catch (e) {
      if (context.mounted) showMessage(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SectionHeader('Continue Watching'),
        SizedBox(
          height: PosterRow.posterWidth * 1.5 + 24,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (context, i) {
              final item = items[i];
              final episode = item.episode;
              return PosterCard(
                title: item.title,
                width: PosterRow.posterWidth,
                progress: item.progress.fraction,
                caption: episode != null ? '${episode.label} ${episode.title}' : item.title.title,
                onTap: () => context.push(
                  '/play',
                  extra: episode != null
                      ? PlayerArgs.episode(show: item.title, episode: episode)
                      : PlayerArgs.movie(item.title),
                ),
                onLongPress: () => _remove(context, ref, item),
              );
            },
          ),
        ),
      ],
    );
  }
}
