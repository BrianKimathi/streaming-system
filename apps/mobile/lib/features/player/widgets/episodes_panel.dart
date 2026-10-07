import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/utils/format.dart';
import '../../../data/models/catalog_models.dart';
import '../../../data/models/misc_models.dart';
import '../../../widgets/artwork.dart';

/// Right-side panel listing a season's episodes, with a season switcher,
/// watch progress and the playing episode highlighted.
class EpisodesPanel extends StatefulWidget {
  const EpisodesPanel({
    super.key,
    required this.show,
    required this.currentEpisodeId,
    required this.progress,
    required this.progressLoading,
    required this.progressError,
    required this.currentFraction,
    required this.onSelect,
    required this.onClose,
  });

  final TvShowDetail show;
  final String currentEpisodeId;

  /// Saved progress by episode id.
  final Map<String, WatchProgress> progress;
  final bool progressLoading;
  final String? progressError;

  /// Live progress of the playing episode (overrides the saved row).
  final double? currentFraction;
  final ValueChanged<Episode> onSelect;
  final VoidCallback onClose;

  @override
  State<EpisodesPanel> createState() => _EpisodesPanelState();
}

class _EpisodesPanelState extends State<EpisodesPanel> {
  late int _season;

  @override
  void initState() {
    super.initState();
    final i = widget.show.seasons.indexWhere((s) => s.episodes.any((e) => e.id == widget.currentEpisodeId));
    _season = i < 0 ? 0 : i;
  }

  @override
  Widget build(BuildContext context) {
    final seasons = widget.show.seasons;
    final season = seasons.isEmpty ? null : seasons[_season.clamp(0, seasons.length - 1)];
    return Material(
      color: AppColors.background.withValues(alpha: 0.97),
      child: SafeArea(
        left: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 4, 4),
              child: Row(
                children: [
                  Expanded(
                    child: seasons.length > 1
                        ? Align(
                            alignment: Alignment.centerLeft,
                            child: DropdownButton<int>(
                              value: _season,
                              underline: const SizedBox.shrink(),
                              dropdownColor: AppColors.surfaceHigh,
                              style: const TextStyle(
                                  color: Colors.white, fontSize: 17, fontWeight: FontWeight.w700),
                              items: [
                                for (var i = 0; i < seasons.length; i++)
                                  DropdownMenuItem(value: i, child: Text(seasons[i].displayName)),
                              ],
                              onChanged: (i) => setState(() => _season = i ?? 0),
                            ),
                          )
                        : Text(
                            season?.displayName ?? 'Episodes',
                            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                          ),
                  ),
                  if (widget.progressLoading)
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 8),
                      child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                    ),
                  IconButton(
                    tooltip: 'Close',
                    icon: const Icon(Icons.close_rounded),
                    onPressed: widget.onClose,
                  ),
                ],
              ),
            ),
            if (widget.progressError != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 6),
                child: Text(
                  "Progress couldn't be loaded: ${widget.progressError}",
                  style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                ),
              ),
            Expanded(
              child: season == null || season.episodes.isEmpty
                  ? const Center(
                      child: Text('No episodes in this season yet.',
                          style: TextStyle(color: AppColors.textSecondary)),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.only(bottom: 16),
                      itemCount: season.episodes.length,
                      itemBuilder: (context, i) {
                        final ep = season.episodes[i];
                        final current = ep.id == widget.currentEpisodeId;
                        final saved = widget.progress[ep.id];
                        final fraction = current
                            ? widget.currentFraction ?? saved?.fraction
                            : (saved == null ? null : (saved.completed ? 1.0 : saved.fraction));
                        return _EpisodeTile(
                          episode: ep,
                          number: ep.episodeNumber ?? i + 1,
                          current: current,
                          watched: saved?.completed ?? false,
                          fraction: fraction,
                          onTap: current ? null : () => widget.onSelect(ep),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EpisodeTile extends StatelessWidget {
  const _EpisodeTile({
    required this.episode,
    required this.number,
    required this.current,
    required this.watched,
    required this.fraction,
    required this.onTap,
  });

  final Episode episode;
  final int number;
  final bool current;
  final bool watched;
  final double? fraction;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final meta = [
      if (current) 'Now playing' else if (watched) 'Watched',
      if (formatRuntime(episode.runtimeMinutes).isNotEmpty) formatRuntime(episode.runtimeMinutes),
    ].join(' · ');
    return Material(
      color: current ? AppColors.surfaceHigh : Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(color: current ? AppColors.accent : Colors.transparent, width: 3),
            ),
          ),
          padding: const EdgeInsets.fromLTRB(13, 8, 16, 8),
          child: Row(
            children: [
              SizedBox(
                width: 120,
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      TitleArtwork(
                        title: episode.title.isEmpty ? episode.label : episode.title,
                        imageUrl: episode.thumbnailUrl,
                      ),
                      Center(
                        child: Icon(
                          current ? Icons.equalizer_rounded : Icons.play_circle_outline_rounded,
                          color: Colors.white70,
                          size: 30,
                        ),
                      ),
                      if (fraction != null)
                        Positioned(
                          left: 0,
                          right: 0,
                          bottom: 0,
                          child: LinearProgressIndicator(
                            value: fraction!.clamp(0.0, 1.0),
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
                      '$number. ${episode.title}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        color: current ? Colors.white : AppColors.textPrimary,
                      ),
                    ),
                    if (meta.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        meta,
                        style: TextStyle(
                          color: current ? AppColors.accent : AppColors.textSecondary,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
