import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../core/config/app_config.dart';
import '../core/theme/app_theme.dart';

const _gradients = <List<Color>>[
  [Color(0xFF7F1D1D), Color(0xFF1E1B4B)],
  [Color(0xFF1E3A8A), Color(0xFF0F172A)],
  [Color(0xFF065F46), Color(0xFF0B0F19)],
  [Color(0xFF6B21A8), Color(0xFF1E1B4B)],
  [Color(0xFF9A3412), Color(0xFF1C1917)],
  [Color(0xFF155E75), Color(0xFF0F172A)],
  [Color(0xFF831843), Color(0xFF1E1B4B)],
  [Color(0xFF3F6212), Color(0xFF111827)],
];

/// Poster/backdrop image with a generated gradient tile (title text on a
/// colour derived from the title) when the title has no artwork or the image
/// fails to load.
class TitleArtwork extends StatelessWidget {
  const TitleArtwork({
    super.key,
    required this.title,
    required this.imageUrl,
    this.backdrop = false,
    this.borderRadius = 6,
  });

  final String title;
  final String? imageUrl;
  final bool backdrop;
  final double borderRadius;

  static bool isHttpUrl(String? url) {
    if (url == null) return false;
    final uri = Uri.tryParse(url.trim());
    return uri != null && (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty;
  }

  @override
  Widget build(BuildContext context) {
    final fallback = GeneratedArtwork(title: title, backdrop: backdrop);
    final url = AppConfig.resolveMediaUrl(imageUrl);
    final Widget child = url != null
        ? CachedNetworkImage(
            imageUrl: url,
            fit: BoxFit.cover,
            fadeInDuration: const Duration(milliseconds: 200),
            placeholder: (_, _) => Container(color: AppColors.surface),
            errorWidget: (_, _, _) => fallback,
          )
        : fallback;
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: child,
    );
  }
}

class GeneratedArtwork extends StatelessWidget {
  const GeneratedArtwork({super.key, required this.title, this.backdrop = false});

  final String title;
  final bool backdrop;

  @override
  Widget build(BuildContext context) {
    final hash = title.codeUnits.fold<int>(7, (h, c) => (h * 31 + c) & 0x7fffffff);
    final colors = _gradients[hash % _gradients.length];
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ),
      ),
      alignment: backdrop ? Alignment.bottomLeft : Alignment.center,
      padding: EdgeInsets.all(backdrop ? 20 : 10),
      child: Text(
        title.isEmpty ? 'StreamX' : title,
        maxLines: backdrop ? 2 : 4,
        overflow: TextOverflow.ellipsis,
        textAlign: backdrop ? TextAlign.left : TextAlign.center,
        style: TextStyle(
          color: Colors.white.withValues(alpha: 0.92),
          fontWeight: FontWeight.w800,
          fontSize: backdrop ? 26 : 15,
          height: 1.15,
          letterSpacing: 0.2,
        ),
      ),
    );
  }
}
