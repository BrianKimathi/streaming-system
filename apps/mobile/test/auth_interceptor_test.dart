import 'package:flutter_test/flutter_test.dart';
import 'package:streamx/core/api/api_client.dart';
import 'package:streamx/core/api/api_exception.dart';
import 'package:streamx/core/storage/session_store.dart';
import 'package:streamx/core/utils/json.dart';

import 'helpers/fakes.dart';

const base = 'https://api.test/api/v1';

const gatewayReject = FakeResponse(401, {'success': false, 'message': 'Invalid or expired token'});

Future<SessionStore> signedInStore({bool withProfile = false}) async {
  final store = SessionStore(InMemoryKeyValueStore());
  await store.saveSignIn(
    accountId: 'acc-1',
    email: 'viewer@example.com',
    accessToken: 'account-old',
    refreshToken: 'refresh-1',
  );
  if (withProfile) await store.saveProfile('prof-1', 'profile-old');
  return store;
}

void main() {
  group('AuthInterceptor', () {
    test('attaches the account token, or the profile token once a profile is selected', () async {
      final store = await signedInStore();
      final adapter = FakeHttpAdapter((_) => FakeResponse.ok([]));
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await api.get('/profiles', parse: (d) => d);
      expect(adapter.requests.last.authorization, 'Bearer account-old');

      await store.saveProfile('prof-1', 'profile-token');
      await api.get('/profiles/me/watchlist', parse: (d) => d);
      expect(adapter.requests.last.authorization, 'Bearer profile-token');
    });

    test('concurrent 401s share a single refresh and every request is retried once', () async {
      final store = await signedInStore();
      final adapter = FakeHttpAdapter((r) {
        if (r.path.endsWith('/auth/refresh')) {
          return FakeResponse.ok(
            {'accountId': 'acc-1', 'email': 'viewer@example.com', 'accessToken': 'account-new', 'refreshToken': 'refresh-2'},
            delay: const Duration(milliseconds: 40),
          );
        }
        return r.authorization == 'Bearer account-new' ? FakeResponse.ok({'path': r.path}) : gatewayReject;
      });
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      final results = await Future.wait([
        api.get('/profiles', parse: (d) => asJson(d)['path']),
        api.get('/devices', parse: (d) => asJson(d)['path']),
        api.get('/subscriptions/me', parse: (d) => asJson(d)['path']),
      ]);

      expect(results, ['/api/v1/profiles', '/api/v1/devices', '/api/v1/subscriptions/me']);
      expect(adapter.requestsTo('/auth/refresh'), hasLength(1));
      expect(adapter.requestsTo('/auth/refresh').single.data,
          {'refreshToken': 'refresh-1'});
      expect(store.accountAccessToken, 'account-new');
      expect(store.refreshToken, 'refresh-2');
      expect(adapter.requestsTo('/profiles'), hasLength(2));
    });

    test('refreshes the profile token with profileId when a profile is selected', () async {
      final store = await signedInStore(withProfile: true);
      final adapter = FakeHttpAdapter((r) {
        if (r.path.endsWith('/auth/refresh')) {
          return FakeResponse.ok({
            'accountId': 'acc-1',
            'email': 'viewer@example.com',
            'accessToken': 'profile-new',
            'refreshToken': 'refresh-2',
          });
        }
        return r.authorization == 'Bearer profile-new' ? FakeResponse.ok(<Object>[]) : gatewayReject;
      });
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await api.get('/profiles/me/watchlist', parse: (d) => d);

      expect(adapter.requestsTo('/auth/refresh').single.data,
          {'refreshToken': 'refresh-1', 'profileId': 'prof-1'});
      expect(store.profileAccessToken, 'profile-new');
      expect(store.accountAccessToken, 'account-old');
      expect(store.profileId, 'prof-1');
    });

    test('404 on profile refresh clears the profile and reports it', () async {
      final store = await signedInStore(withProfile: true);
      String? goneMessage;
      var expired = false;
      final adapter = FakeHttpAdapter((r) => r.path.endsWith('/auth/refresh')
          ? FakeResponse.error(404, 'Profile not found')
          : gatewayReject);
      final api = ApiClient(buildApiDio(
        store: store,
        baseUrl: base,
        adapter: adapter,
        onProfileGone: (m) => goneMessage = m,
        onSessionExpired: () => expired = true,
      ));

      await expectLater(
        api.get('/watch-history/continue-watching', parse: (d) => d),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 404)),
      );
      expect(goneMessage, 'Profile not found');
      expect(expired, isFalse);
      expect(store.profileId, isNull);
      expect(store.profileAccessToken, isNull);
      expect(store.refreshToken, 'refresh-1', reason: 'the account session is kept');
    });

    test('rejected refresh token clears the session and reports expiry', () async {
      final store = await signedInStore();
      var expired = false;
      final adapter = FakeHttpAdapter((r) => r.path.endsWith('/auth/refresh')
          ? FakeResponse.error(401, 'Invalid refresh token')
          : gatewayReject);
      final api = ApiClient(buildApiDio(
        store: store,
        baseUrl: base,
        adapter: adapter,
        onSessionExpired: () => expired = true,
      ));

      await expectLater(
        api.get('/profiles', parse: (d) => d),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', contains('session expired'))),
      );
      expect(expired, isTrue);
      expect(store.hasSession, isFalse);
      expect(store.accountAccessToken, isNull);
    });

    test('a retried request that fails again is not refreshed twice', () async {
      final store = await signedInStore();
      final adapter = FakeHttpAdapter((r) => r.path.endsWith('/auth/refresh')
          ? FakeResponse.ok({'accountId': 'acc-1', 'accessToken': 'account-new', 'refreshToken': 'refresh-2'})
          : gatewayReject);
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await expectLater(api.get('/profiles', parse: (d) => d), throwsA(isA<ApiException>()));
      expect(adapter.requestsTo('/auth/refresh'), hasLength(1));
      expect(adapter.requestsTo('/profiles'), hasLength(2));
    });

    test('a wrong PIN (domain 401) is not refreshed or retried', () async {
      final store = await signedInStore();
      final adapter = FakeHttpAdapter((r) => FakeResponse.error(401, 'Incorrect PIN code.'));
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await expectLater(
        api.post('/profiles/prof-1/select', query: {'pin': '0000'}, domain401: true, parse: (d) => d),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Incorrect PIN code.')),
      );
      expect(adapter.requestsTo('/auth/refresh'), isEmpty);
      expect(adapter.requestsTo('/select'), hasLength(1));
    });

    test('login failures are returned as-is without a refresh', () async {
      final store = SessionStore(InMemoryKeyValueStore());
      final adapter = FakeHttpAdapter((r) => FakeResponse.error(401, 'Invalid email or password'));
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await expectLater(
        api.post('/auth/login', body: {'usernameOrEmail': 'a@b.co', 'password': 'x'}, parse: (d) => d),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Invalid email or password')),
      );
      expect(adapter.requestsTo('/auth/refresh'), isEmpty);
      expect(adapter.requests.single.authorization, isNull);
    });

    test('5xx on refresh keeps the session', () async {
      final store = await signedInStore();
      var expired = false;
      final adapter = FakeHttpAdapter((r) => r.path.endsWith('/auth/refresh')
          ? FakeResponse.error(503, 'Service unavailable')
          : gatewayReject);
      final api = ApiClient(buildApiDio(
        store: store,
        baseUrl: base,
        adapter: adapter,
        onSessionExpired: () => expired = true,
      ));

      await expectLater(api.get('/profiles', parse: (d) => d), throwsA(isA<ApiException>()));
      expect(expired, isFalse);
      expect(store.hasSession, isTrue);
    });
  });

  group('ApiClient envelope', () {
    test('unwraps data and surfaces validation messages', () async {
      final store = SessionStore(InMemoryKeyValueStore());
      final adapter = FakeHttpAdapter((r) => r.path.endsWith('/auth/register')
          ? const FakeResponse(400, {
              'success': false,
              'message': 'Validation failed',
              'errors': {'password': 'Password must be at least 8 characters long'},
            })
          : FakeResponse.ok({'value': 42}));
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      expect(await api.get('/anything', parse: (d) => asJson(d)['value']), 42);
      await expectLater(
        api.post('/auth/register', body: {}, parse: (d) => d),
        throwsA(isA<ApiException>()
            .having((e) => e.message, 'message', 'Password must be at least 8 characters long')
            .having((e) => e.statusCode, 'status', 400)),
      );
    });

    test('503 keeps the honest server message', () async {
      final store = SessionStore(InMemoryKeyValueStore());
      final adapter = FakeHttpAdapter((_) => FakeResponse.error(503, 'M-Pesa payments are not configured yet'));
      final api = ApiClient(buildApiDio(store: store, baseUrl: base, adapter: adapter));

      await expectLater(
        api.post('/billing/checkout', body: {}, parse: (d) => d),
        throwsA(isA<ApiException>()
            .having((e) => e.message, 'message', 'M-Pesa payments are not configured yet')
            .having((e) => e.isUnavailable, 'unavailable', isTrue)),
      );
    });
  });
}
