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
}
