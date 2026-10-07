import 'package:dio/dio.dart';

/// Thrown by the session interceptor when the refresh token is no longer
/// accepted. The session has already been cleared when this is raised.
class SessionExpiredException implements Exception {
  const SessionExpiredException();

  @override
  String toString() => 'Your session expired. Please sign in again.';
}

/// Thrown by the session interceptor when refreshing a profile token returns
/// 404: the selected profile no longer exists. The profile selection has
/// already been cleared when this is raised.
class ProfileGoneException implements Exception {
  const ProfileGoneException(this.message);
  final String message;

  @override
  String toString() => message;
}

class ApiException implements Exception {
  const ApiException(
    this.message, {
    this.statusCode,
    this.isNetworkError = false,
    this.fieldErrors = const {},
  });

  final String message;
  final int? statusCode;
  final bool isNetworkError;
  final Map<String, String> fieldErrors;

  bool get isNotFound => statusCode == 404;
  bool get isUnauthorized => statusCode == 401;
  bool get isForbidden => statusCode == 403;
  bool get isConflict => statusCode == 409;
  bool get isUnavailable => statusCode == 503;

  static const networkMessage =
      "Can't reach StreamX right now. Check your internet connection and try again.";

  factory ApiException.fromDio(DioException e) {
    final inner = e.error;
    if (inner is ApiException) return inner;
    if (inner is SessionExpiredException) {
      return ApiException(inner.toString(), statusCode: 401);
    }
    if (inner is ProfileGoneException) {
      return ApiException(inner.message, statusCode: 404);
    }

    final response = e.response;
    if (response == null) {
      switch (e.type) {
        case DioExceptionType.connectionTimeout:
        case DioExceptionType.sendTimeout:
        case DioExceptionType.receiveTimeout:
          return const ApiException(
            'StreamX took too long to respond. Please try again.',
            isNetworkError: true,
          );
        case DioExceptionType.cancel:
          return const ApiException('Request cancelled.', isNetworkError: true);
        default:
          return const ApiException(networkMessage, isNetworkError: true);
      }
    }

    final status = response.statusCode;
    final body = response.data;
    String? message;
    final fieldErrors = <String, String>{};
    if (body is Map) {
      final m = body['message'];
      if (m is String && m.trim().isNotEmpty) message = m.trim();
      final errors = body['errors'];
      if (errors is Map) {
        errors.forEach((k, v) {
          if (v != null) fieldErrors['$k'] = '$v';
        });
      }
    }
    if (fieldErrors.isNotEmpty) {
      final details = fieldErrors.values.join('\n');
      message = message == null || message == 'Validation failed'
          ? details
          : '$message\n$details';
    }
    message ??= _fallbackFor(status);
    return ApiException(message, statusCode: status, fieldErrors: fieldErrors);
  }

  static String _fallbackFor(int? status) {
    switch (status) {
      case 400:
        return 'The request was rejected by the server.';
      case 401:
        return 'Please sign in again.';
      case 403:
        return "You don't have access to this.";
      case 404:
        return 'Not found.';
      case 409:
        return 'This action conflicts with the current state.';
      case 502:
      case 503:
      case 504:
        return 'StreamX is temporarily unavailable. Please try again shortly.';
      default:
        return 'Something went wrong (HTTP ${status ?? '?'}).';
    }
  }

  @override
  String toString() => message;
}

/// Human readable message for any error surfaced to the UI.
String errorMessage(Object error) {
  if (error is ApiException) return error.message;
  if (error is DioException) return ApiException.fromDio(error).message;
  if (error is SessionExpiredException) return error.toString();
  if (error is ProfileGoneException) return error.message;
  return 'Something went wrong. Please try again.';
}
