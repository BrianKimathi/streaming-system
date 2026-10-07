import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../storage/session_store.dart';
import 'api_exception.dart';
import 'auth_interceptor.dart';

typedef Parser<T> = T Function(Object? data);

BaseOptions defaultApiOptions([String? baseUrl]) => BaseOptions(
      baseUrl: baseUrl ?? AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
      headers: {'Accept': 'application/json'},
    );

/// Builds the main client: the session interceptor plus a bare client for
/// token refresh. [adapter] lets tests plug in a fake transport.
Dio buildApiDio({
  required SessionStore store,
  String? baseUrl,
  HttpClientAdapter? adapter,
  void Function()? onSessionExpired,
  void Function(String message)? onProfileGone,
}) {
  final dio = Dio(defaultApiOptions(baseUrl));
  final refreshDio = Dio(defaultApiOptions(baseUrl));
  if (adapter != null) {
    dio.httpClientAdapter = adapter;
    refreshDio.httpClientAdapter = adapter;
  }
  dio.interceptors.add(AuthInterceptor(
    store: store,
    retryClient: dio,
    refreshClient: refreshDio,
    onSessionExpired: onSessionExpired,
    onProfileGone: onProfileGone,
  ));
  return dio;
}

/// Thin wrapper over Dio that unwraps the backend envelope
/// `{success, message, data}` and converts failures into [ApiException]
/// carrying the server's message.
class ApiClient {
  ApiClient(this.dio);

  final Dio dio;

  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    required Parser<T> parse,
  }) =>
      _send(() => dio.get<dynamic>(path, queryParameters: query), parse);

  Future<T> post<T>(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
    required Parser<T> parse,
    bool domain401 = false,
  }) =>
      _send(
        () => dio.post<dynamic>(
          path,
          data: body,
          queryParameters: query,
          options: domain401 ? Options(extra: {kDomain401: true}) : null,
        ),
        parse,
      );

  Future<T> put<T>(
    String path, {
    Object? body,
    required Parser<T> parse,
  }) =>
      _send(() => dio.put<dynamic>(path, data: body), parse);

  Future<T> delete<T>(
    String path, {
    Object? body,
    required Parser<T> parse,
  }) =>
      _send(() => dio.delete<dynamic>(path, data: body), parse);

  static Object? ignore(Object? _) => null;

  Future<T> _send<T>(Future<Response<dynamic>> Function() call, Parser<T> parse) async {
    final Response<dynamic> response;
    try {
      response = await call();
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
    final body = response.data;
    if (body is Map) {
      if (body['success'] == false) {
        final message = body['message'];
        throw ApiException(
          message is String && message.isNotEmpty ? message : 'The request failed.',
          statusCode: response.statusCode,
        );
      }
      try {
        return parse(body['data']);
      } on FormatException catch (e) {
        throw ApiException('Unexpected response from StreamX (${e.message}).',
            statusCode: response.statusCode);
      } on TypeError {
        throw ApiException('Unexpected response from StreamX.',
            statusCode: response.statusCode);
      }
    }
    try {
      return parse(null);
    } on Object {
      throw ApiException('Unexpected response from StreamX.',
          statusCode: response.statusCode);
    }
  }
}
