import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../widgets/poster_card.dart';
import '../../widgets/state_views.dart';
import '../home/home_providers.dart';
import 'search_controller.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _query = TextEditingController();
  final _scroll = ScrollController();
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _query.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scroll.position.pixels > _scroll.position.maxScrollExtent - 600) {
      ref.read(searchControllerProvider.notifier).loadMore();
    }
  }

  void _onChanged(String value) {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      ref.read(searchControllerProvider.notifier).setQuery(value);
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(searchControllerProvider);
    final genres = ref.watch(genresProvider);
    final controller = ref.read(searchControllerProvider.notifier);

    // Keep paging when the filtered results don't fill the viewport yet.
    if (state.started && !state.loading && !state.loadingMore && state.hasMore && state.error == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _scroll.hasClients && _scroll.position.maxScrollExtent <= 0) {
          controller.loadMore();
        }
      });
    }

    Widget body;
    if (state.loading) {
      body = const SliverFillRemaining(hasScrollBody: false, child: LoadingView());
    } else if (state.error != null && state.items.isEmpty) {
      body = SliverFillRemaining(
        hasScrollBody: false,
        child: ErrorView(error: state.error!, onRetry: controller.clearErrorAndRetry),
      );
    } else if (state.items.isEmpty) {
      final filtered = state.query.isNotEmpty || state.genreId != null;
      body = SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyView(
          icon: filtered ? Icons.search_off_rounded : Icons.movie_filter_outlined,
          title: filtered ? 'No matches' : 'No titles have been published yet',
          message: filtered
              ? state.query.isNotEmpty
                  ? 'Nothing matches "${state.query}". Try a different title.'
                  : 'No titles in this genre yet.'
              : null,
        ),
      );
    } else {
      body = SliverPadding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
        sliver: SliverGrid(
          gridDelegate: posterGridDelegate(),
          delegate: SliverChildBuilderDelegate(
            (context, i) => PosterCard(title: state.items[i]),
            childCount: state.items.length,
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _query,
          onChanged: _onChanged,
          textInputAction: TextInputAction.search,
          onSubmitted: (v) {
            _debounce?.cancel();
            controller.setQuery(v);
          },
          decoration: InputDecoration(
            hintText: 'Search movies and shows',
            prefixIcon: const Icon(Icons.search_rounded),
            isDense: true,
            suffixIcon: _query.text.isEmpty
                ? null
                : IconButton(
                    icon: const Icon(Icons.close_rounded),
                    onPressed: () {
                      _query.clear();
                      _onChanged('');
                    },
                  ),
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: CustomScrollView(
          controller: _scroll,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: genres.when(
                loading: () => const SizedBox(height: 8),
                error: (e, _) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Text("Genres couldn't be loaded.",
                            style: TextStyle(color: AppColors.textMuted, fontSize: 13)),
                      ),
                      TextButton(
                        onPressed: () => ref.invalidate(genresProvider),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
                data: (list) => list.isEmpty
                    ? const SizedBox(height: 8)
                    : SizedBox(
                        height: 52,
                        child: ListView(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(right: 8),
                              child: ChoiceChip(
                                label: const Text('All'),
                                selected: state.genreId == null,
                                onSelected: (_) => controller.setGenre(null),
                              ),
                            ),
                            for (final g in list)
                              Padding(
                                padding: const EdgeInsets.only(right: 8),
                                child: ChoiceChip(
                                  label: Text(g.name),
                                  selected: state.genreId == g.id,
                                  onSelected: (sel) => controller.setGenre(sel ? g.id : null),
                                ),
                              ),
                          ],
                        ),
                      ),
              ),
            ),
            body,
            if (state.loadingMore)
              const SliverToBoxAdapter(
                child: Padding(padding: EdgeInsets.all(16), child: LoadingView()),
              ),
            if (state.error != null && state.items.isNotEmpty)
              SliverToBoxAdapter(
                child: ErrorView(error: state.error!, compact: true, onRetry: controller.clearErrorAndRetry),
              ),
          ],
        ),
      ),
    );
  }
}
