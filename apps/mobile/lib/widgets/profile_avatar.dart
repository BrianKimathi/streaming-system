import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import 'artwork.dart';

/// Built-in avatar palette; profiles store `avatar:<index>` as avatarUrl.
class AvatarPalette {
  AvatarPalette._();

  static const count = 12;

  static const _colors = <Color>[
    Color(0xFFDC2626),
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFD97706),
    Color(0xFF9333EA),
    Color(0xFF0891B2),
    Color(0xFFDB2777),
    Color(0xFF65A30D),
    Color(0xFFEA580C),
    Color(0xFF4F46E5),
    Color(0xFF0D9488),
    Color(0xFF64748B),
  ];

  static const _icons = <IconData>[
    Icons.sentiment_satisfied_alt_rounded,
    Icons.rocket_launch_rounded,
    Icons.pets_rounded,
    Icons.star_rounded,
    Icons.music_note_rounded,
    Icons.sports_esports_rounded,
    Icons.favorite_rounded,
    Icons.eco_rounded,
    Icons.local_fire_department_rounded,
    Icons.auto_awesome_rounded,
    Icons.waves_rounded,
    Icons.face_rounded,
  ];

  static Color color(int index) => _colors[index % count];
  static IconData icon(int index) => _icons[index % count];
  static String value(int index) => 'avatar:$index';

  static int? indexOf(String? avatarUrl) {
    if (avatarUrl == null || !avatarUrl.startsWith('avatar:')) return null;
    final i = int.tryParse(avatarUrl.substring(7));
    return i == null ? null : i % count;
  }
}

class ProfileAvatar extends StatelessWidget {
  const ProfileAvatar({
    super.key,
    required this.avatarUrl,
    required this.name,
    this.size = 96,
  });

  final String? avatarUrl;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(size * 0.12);
    final url = avatarUrl;
    if (TitleArtwork.isHttpUrl(url)) {
      return ClipRRect(
        borderRadius: radius,
        child: CachedNetworkImage(
          imageUrl: url!,
          width: size,
          height: size,
          fit: BoxFit.cover,
          errorWidget: (_, _, _) => _tile(radius, _fallbackIndex()),
        ),
      );
    }
    return _tile(radius, AvatarPalette.indexOf(url) ?? _fallbackIndex());
  }

  int _fallbackIndex() =>
      name.codeUnits.fold<int>(0, (h, c) => (h + c) & 0x7fffffff) % AvatarPalette.count;

  Widget _tile(BorderRadius radius, int index) {
    final color = AvatarPalette.color(index);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: radius,
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [color, Color.lerp(color, Colors.black, 0.45)!],
        ),
      ),
      child: Icon(AvatarPalette.icon(index), color: Colors.white, size: size * 0.5),
    );
  }
}
