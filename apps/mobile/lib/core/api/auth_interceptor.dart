import 'package:dio/dio.dart';

import '../storage/session_store.dart';
import 'api_exception.dart';

/// Set on requests whose endpoint can legitimately answer 401 for a domain
/// reason (e.g. "Incorrect PIN code."). For those, a refresh is only
/// attempted when the 401 came from the gateway's token check, so a wrong
/// PIN is never retried (which would count as a second attempt).
const kDomain401 = 'sx.domain401';

const _kTokenUsed = 'sx.tokenUsed';
const _kRetried = 'sx.retried';

const _gatewayTokenMessages = {'Invalid or expired token', 'Authentication required'};

enum _RefreshStatus { refreshed, expired, profileGone, transientFailure }

class _RefreshResult {
  const _RefreshResult(this.status, {this.error, this.message});
  final _RefreshStatus status;
  final DioException? error;
  final String? message;
}

/// Attaches the bearer token (profile token when a profile is selected,
/// otherwise the account token) and transparently refreshes it on 401.
///
/// Refresh is single-flight: concurrent 401s share one `POST /auth/refresh`.
/// Each request is retried at most once.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required this.store,
    required this.retryClient,
    required this.refreshClient,
    this.onSessionExpired,
    this.onProfileGone,
  });

  final SessionStore store;

  /// Client used to replay the original request (normally the main client,
  /// so the replay goes through this interceptor and picks up the new token).
  final Dio retryClient;

  /// Bare client without this interceptor, used for `POST /auth/refresh`.
  final Dio refreshClient;

  final void Function()? onSessionExpired;
  final void Function(String message)? onProfileGone;

  Future<_RefreshResult>? _inflight;

  static bool _isAuthEndpoint(String path) {
    final p = Uri.parse(path).path;
    return p.endsWith('/auth/login') ||
        p.endsWith('/auth/register') ||
        p.endsWith('/auth/refresh');
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (!_isAuthEndpoint(options.path)) {
      final token = store.activeAccessToken;
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
        options.extra[_kTokenUsed] = token;
      } else {
        options.headers.remove('Authorization');
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    final response = err.response;
    if (response?.statusCode != 401 ||
        _isAuthEndpoint(options.path) ||
        options.extra[_kRetried] == true ||
        !store.hasSession) {
      return handler.next(err);
    }
    if (options.extra[kDomain401] == true && !_isGatewayTokenRejection(response)) {
      return handler.next(err);
    }

    final usedToken = options.extra[_kTokenUsed];
    final current = store.activeAccessToken;
    if (current == null || current == usedToken) {
      final result = await _refreshSingleFlight();
      switch (result.status) {
        case _RefreshStatus.refreshed:
          break;
        case _RefreshStatus.expired:
          return handler.reject(DioException(
            requestOptions: options,
            response: response,
            type: DioExceptionType.badResponse,
            error: const SessionExpiredException(),
          ));
        case _RefreshStatus.profileGone:
          return handler.reject(DioException(
            requestOptions: options,
            response: response,
            type: DioExceptionType.badResponse,
            error: ProfileGoneException(result.message ?? 'Profile not found'),
          ));
        case _RefreshStatus.transientFailure:
          return handler.reject(result.error ?? err);
      }
    }

    try {
      final headers = Map<String, dynamic>.of(options.headers)..remove('Authorization');
      final replay = options.copyWith(
        headers: headers,
        extra: {...options.extra, _kRetried: true},
      );
      final retried = await retryClient.fetch<dynamic>(replay);
      handler.resolve(retried);
    } on DioException catch (e) {
      handler.next(e);
    }
  }

  static bool _isGatewayTokenRejection(Response<dynamic>? response) {
    final data = response?.data;
    if (data is Map) {
      final message = data['message'];
      return message is String && _gatewayTokenMessages.contains(message.trim());
    }
    return false;
  }

  Future<_RefreshResult> _refreshSingleFlight() {
    final existing = _inflight;
    if (existing != null) return existing;
    final future = _refresh();
    _inflight = future;
    future.whenComplete(() {
      if (identical(_inflight, future)) _inflight = null;
    }).ignore();
    return future;
  }

  Future<_RefreshResult> _refresh() async {
    final refreshToken = store.refreshToken;
    if (refreshToken == null) {
      await store.clear();
      onSessionExpired?.call();
      return const _RefreshResult(_RefreshStatus.expired);
    }
    final profileId = store.profileId;
    try {
      final response = await refreshClient.post<dynamic>(
        '/auth/refresh',
        data: {
          'refreshToken': refreshToken,
          'profileId': ?profileId,
        },
      );
      final body = response.data;
      final data = body is Map ? body['data'] : null;
      final accessToken = data is Map ? data['accessToken'] : null;
      if (accessToken is! String || accessToken.isEmpty) {
        await store.clear();
        onSessionExpired?.call();
        return const _RefreshResult(_RefreshStatus.expired);
      }
      final newRefresh = data is Map ? data['refreshToken'] : null;
      if (profileId != null && store.profileId != profileId) {
        // The user switched profile while the refresh was in flight; the
        // token belongs to the old profile and must not be stored as current.
        if (newRefresh is String) await store.saveRefreshToken(newRefresh);
        return const _RefreshResult(_RefreshStatus.refreshed);
      }
      await store.saveRefreshed(
        accessToken: accessToken,
        refreshToken: newRefresh is String ? newRefresh : null,
        forProfile: profileId != null,
      );
      return const _RefreshResult(_RefreshStatus.refreshed);
    } on DioException catch (e) {
      final status = e.response?.statusCode;
      if (status == null || status >= 500) {
        return _RefreshResult(_RefreshStatus.transientFailure, error: e);
      }
      final message = _messageOf(e.response);
      if (status == 404 && profileId != null && _mentionsProfile(message)) {
        await store.clearProfile();
        onProfileGone?.call(message ?? 'Profile not found');
        return _RefreshResult(_RefreshStatus.profileGone, message: message);
      }
      await store.clear();
      onSessionExpired?.call();
      return const _RefreshResult(_RefreshStatus.expired);
    }
  }

  static String? _messageOf(Response<dynamic>? response) {
    final data = response?.data;
    if (data is Map && data['message'] is String) return data['message'] as String;
    return null;
  }

  static bool _mentionsProfile(String? message) =>
      message == null || message.toLowerCase().contains('profile');
}
