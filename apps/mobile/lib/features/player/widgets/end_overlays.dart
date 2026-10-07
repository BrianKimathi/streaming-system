import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../../../data/models/catalog_models.dart';
import '../../../widgets/artwork.dart';

/// White "Next Episode" pill shown during the last seconds of an episode.
class NextEpisodePill extends StatelessWidget {
  const NextEpisodePill({super.key, required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: const StadiumBorder(),
      elevation: 4,
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onPressed,
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.skip_next_rounded, color: Colors.black),
              SizedBox(width: 6),
              Text('Next Episode', style: TextStyle(color: Colors.black, fontWeight: FontWeight.w700)),
            ],
          ),
        ),
      ),
    );
  }
}

/// End-of-episode card. With a [countdown] it reads "Next episode in 5…" and
/// the screen plays the episode when it reaches zero.
class NextEpisodeCard extends StatelessWidget {
  const NextEpisodeCard({
    super.key,
    required this.episode,
    required this.fallbackImageUrl,
    required this.countdown,
    required this.onPlayNow,
    required this.onCancel,
  });

  final Episode episode;
  final String? fallbackImageUrl;
  final int? countdown;
  final VoidCallback onPlayNow;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 360,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceHigh.withValues(alpha: 0.96),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 128,
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: TitleArtwork(
                    title: episode.title.isEmpty ? episode.label : episode.title,
                    imageUrl: episode.thumbnailUrl ?? fallbackImageUrl,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      countdown != null ? 'Next episode in $countdown' : 'Next episode',
                      style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${episode.label}  ${episode.title}',
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w700, color: Colors.white),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: onPlayNow,
                  style: FilledButton.styleFrom(minimumSize: const Size(0, 40)),
                  icon: const Icon(Icons.play_arrow_rounded),
                  label: const Text('Play now'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  onPressed: onCancel,
                  style: OutlinedButton.styleFrom(minimumSize: const Size(0, 40)),
                  child: const Text('Cancel'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Shown when a movie, trailer or the final episode finishes.
class PlaybackEndOverlay extends StatelessWidget {
  const PlaybackEndOverlay({
    super.key,
    required this.heading,
    required this.onWatchAgain,
    required this.onBack,
  });

  final String heading;
  final VoidCallback onWatchAgain;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Colors.black.withValues(alpha: 0.72),
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                heading,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 20),
              Wrap(
                spacing: 12,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  FilledButton.icon(
                    onPressed: onWatchAgain,
                    icon: const Icon(Icons.replay_rounded),
                    label: const Text('Watch again'),
                  ),
                  OutlinedButton.icon(
                    onPressed: onBack,
                    icon: const Icon(Icons.arrow_back_rounded),
                    label: const Text('Back'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
