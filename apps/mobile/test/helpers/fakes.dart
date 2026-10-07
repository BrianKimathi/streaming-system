import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:streamx/core/storage/session_store.dart';

/// Test-only in-memory replacement for secure storage.
class InMemoryKeyValueStore implements KeyValueStore {
  final Map<String, String> values = {};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String? value) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }
}

class FakeResponse {
  const FakeResponse(this.status, this.body, {this.delay = Duration.zero});

  final int status;
  final Object? body;
  final Duration delay;

  static FakeResponse ok(Object? data, {Duration delay = Duration.zero}) =>
      FakeResponse(200, {'success': true, 'message': null, 'data': data}, delay: delay);

  static FakeResponse error(int status, String message) =>
      FakeResponse(status, {'success': false, 'message': message, 'data': null});
}

class RecordedRequest {
  RecordedRequest(this.method, this.path, this.headers, this.data, this.query);

  final String method;
  final String path;
  final Map<String, dynamic> headers;
  final Object? data;
  final Map<String, dynamic> query;

  String? get authorization => headers['Authorization'] as String?;
}

typedef FakeHandler = FakeResponse Function(RecordedRequest request);

/// Test-only transport: routes requests to [handler] and records them.
class FakeHttpAdapter implements HttpClientAdapter {
  FakeHttpAdapter(this.handler);

  FakeHandler handler;
  final List<RecordedRequest> requests = [];

  List<RecordedRequest> requestsTo(String path) =>
      requests.where((r) => r.path.endsWith(path)).toList();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final request = RecordedRequest(
      options.method,
      options.uri.path,
      Map<String, dynamic>.of(options.headers),
      options.data,
      Map<String, dynamic>.of(options.queryParameters),
    );
    requests.add(request);
    final response = handler(request);
    if (response.delay > Duration.zero) await Future<void>.delayed(response.delay);
    return ResponseBody.fromString(
      jsonEncode(response.body),
      response.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
