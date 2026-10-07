import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../data/content_filter.dart';
import '../../data/models/catalog_models.dart';
import '../../widgets/poster_card.dart';
import '../../widgets/state_views.dart';
import '../title/title_providers.dart';

/// Watchlist entries resolved to catalog titles, newest first. Entries whose
/// title is no longer published are omitted by the lookup.
final myListTitlesProvider = FutureProvider.autoDispose<List<CatalogTitle>>((ref) async {
  final items = await ref.watch(watchlistProvider.future);
  if (items.isEmpty) return const [];
  final profile = ref.read(currentProfileProvider);
  final lookup = await ref.watch(catalogRepositoryProvider).lookup(items.map((i) => i.titleId));
  final byId = lookup.titlesById;
  return filterForProfile(
    [for (final i in items) if (byId[i.titleId] != null) byId[i.titleId]!],
    profile,
  );
});

class MyListScreen extends ConsumerWidget {
  const MyListScreen({super.key});

  Future<void> _remove(BuildContext context, WidgetRef ref, CatalogTitle title) async {
    final ok = await confirm(
      context,
      title: 'Remove from My List?',
      message: '"${title.title}" will be removed from My List.',
      confirmLabel: 'Remove',
      destructive: true,
    );
    if (!ok || !context.mounted) return;
    try {
      await ref.read(profileRepositoryProvider).removeFromWatchlist(title.id);
      ref.invalidate(watchlistProvider);
      if (context.mounted) showMessage(context, 'Removed from My List.');
    } on ApiException catch (e) {
      if (context.mounted) showMessage(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final titles = ref.watch(myListTitlesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My List')),
      body: titles.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(
          error: e,
          onRetry: () {
            ref.invalidate(watchlistProvider);
            ref.invalidate(myListTitlesProvider);
          },
        ),
        data: (list) {
          if (list.isEmpty) {
            return RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(watchlistProvider);
                await ref.read(myListTitlesProvider.future);
              },
              child: ListView(
                children: [
                  SizedBox(
                    height: MediaQuery.sizeOf(context).height * 0.6,
                    child: EmptyView(
                      icon: Icons.bookmark_outline_rounded,
                      title: 'Your list is empty',
                      message: 'Add movies and shows to My List to find them here.',
                      action: OutlinedButton(
                        onPressed: () => context.go('/search'),
                        child: const Text('Browse titles'),
                      ),
                    ),
                  ),
                ],
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(watchlistProvider);
              await ref.read(myListTitlesProvider.future);
            },
            child: GridView.builder(
              padding: const EdgeInsets.all(16),
              gridDelegate: posterGridDelegate(),
              itemCount: list.length,
              itemBuilder: (context, i) => PosterCard(
                title: list[i],
                onLongPress: () => _remove(context, ref, list[i]),
              ),
            ),
          );
        },
      ),
    );
  }
}
