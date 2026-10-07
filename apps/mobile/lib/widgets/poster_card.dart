import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_theme.dart';
import '../data/models/catalog_models.dart';
import 'artwork.dart';
import 'state_views.dart';

void openTitle(BuildContext context, CatalogTitle title) =>
    context.push('/title/${title.kind.routeValue}/${title.id}');

class PosterCard extends StatelessWidget {
  const PosterCard({
    super.key,
    required this.title,
    this.onTap,
    this.onLongPress,
    this.progress,
    this.caption,
    this.width,
  });

  final CatalogTitle title;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// 0..1 watch progress drawn under the artwork.
  final double? progress;
  final String? caption;
  final double? width;

  @override
  Widget build(BuildContext context) {
    final card = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        AspectRatio(
          aspectRatio: 2 / 3,
          child: Stack(
            fit: StackFit.expand,
            children: [
              TitleArtwork(title: title.title, imageUrl: title.posterUrl),
              if (progress != null)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: ClipRRect(
                    borderRadius: const BorderRadius.vertical(bottom: Radius.circular(6)),
                    child: LinearProgressIndicator(
                      value: progress!.clamp(0.0, 1.0),
                      minHeight: 4,
                      backgroundColor: Colors.black54,
                      color: AppColors.accent,
                    ),
                  ),
                ),
            ],
          ),
        ),
        if (caption != null) ...[
          const SizedBox(height: 6),
          Text(
            caption!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
          ),
        ],
      ],
    );
    return SizedBox(
      width: width,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(6),
          onTap: onTap ?? () => openTitle(context, title),
          onLongPress: onLongPress,
          child: card,
        ),
      ),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 10),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
      ),
    );
  }
}

/// Horizontal row of posters.
class PosterRow extends StatelessWidget {
  const PosterRow({super.key, required this.title, required this.items});

  final String title;
  final List<CatalogTitle> items;

  static const posterWidth = 112.0;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(title),
        SizedBox(
          height: posterWidth * 1.5,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (context, i) => PosterCard(title: items[i], width: posterWidth),
          ),
        ),
      ],
    );
  }
}

class PosterRowSkeleton extends StatelessWidget {
  const PosterRowSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 20, 16, 10),
          child: SkeletonBox(width: 140, height: 18),
        ),
        SizedBox(
          height: PosterRow.posterWidth * 1.5,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            physics: const NeverScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: 4,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (_, _) => const SkeletonBox(width: PosterRow.posterWidth, radius: 6),
          ),
        ),
      ],
    );
  }
}

/// Responsive poster grid delegate (2:3 tiles).
SliverGridDelegate posterGridDelegate({double extraHeight = 0}) =>
    SliverGridDelegateWithMaxCrossAxisExtent(
      maxCrossAxisExtent: 140,
      mainAxisSpacing: 12,
      crossAxisSpacing: 10,
      childAspectRatio: 140 / (210 + extraHeight),
    );
