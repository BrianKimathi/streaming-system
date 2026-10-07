/// Build-time configuration. Override with
/// `--dart-define=API_BASE_URL=https://host/api/v1`.
class AppConfig {
  AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://streamxapi.briankimathi.dev/api/v1',
  );

  /// Scheme + host (+ port) of [apiBaseUrl]. Stream URLs returned by the
  /// playback service are absolute paths (`/api/v1/media/stream/...`) and are
  /// resolved against this origin.
  static String get apiOrigin => originOf(apiBaseUrl);

  static String originOf(String baseUrl) {
    final uri = Uri.parse(baseUrl);
    return uri.hasPort &&
            !((uri.scheme == 'https' && uri.port == 443) ||
                (uri.scheme == 'http' && uri.port == 80))
        ? '${uri.scheme}://${uri.host}:${uri.port}'
        : '${uri.scheme}://${uri.host}';
  }

  static String resolveStreamUrl(String streamUrl) {
    if (streamUrl.startsWith('http://') || streamUrl.startsWith('https://')) {
      return streamUrl;
    }
    final path = streamUrl.startsWith('/') ? streamUrl : '/$streamUrl';
    return '$apiOrigin$path';
  }

  /// Normalises an artwork/trailer URL from the catalog: absolute `http(s)`
  /// URLs (media-service files or external links) are kept, protocol-relative
  /// `//host/...` becomes https, and relative paths (`/api/v1/media/files/...`)
  /// are resolved against [origin] (defaults to [apiOrigin]). Returns null for
  /// blank values and non-web schemes.
  static String? resolveMediaUrl(String? url, {String? origin}) {
    final raw = url?.trim();
    if (raw == null || raw.isEmpty) return null;
    if (raw.startsWith('//')) return _webUrlOrNull('https:$raw');
    final uri = Uri.tryParse(raw);
    if (uri == null) return null;
    if (uri.hasScheme) return _webUrlOrNull(raw);
    final base = origin ?? apiOrigin;
    return raw.startsWith('/') ? '$base$raw' : '$base/$raw';
  }

  static String? _webUrlOrNull(String url) {
    final uri = Uri.tryParse(url);
    final web = uri != null && (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty;
    return web ? url : null;
  }

  static const _videoExtensions = ['.mp4', '.m4v', '.mov', '.webm', '.m3u8'];

  /// Whether [url] points straight at a playable video file (an uploaded
  /// trailer or a direct MP4/HLS link) rather than a web page such as YouTube.
  static bool isDirectVideoUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return false;
    final path = uri.path.toLowerCase();
    return path.contains('/api/v1/media/files/') || _videoExtensions.any(path.endsWith);
  }

  static bool isHlsUrl(String url) =>
      (Uri.tryParse(url)?.path.toLowerCase() ?? '').endsWith('.m3u8');
}
