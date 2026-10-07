import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:streamx/core/api/api_client.dart';
import 'package:streamx/core/providers.dart';
import 'package:streamx/core/storage/session_store.dart';
import 'package:streamx/features/auth/login_screen.dart';

import 'helpers/fakes.dart';

void main() {
  late FakeHttpAdapter adapter;

  Future<void> pumpLogin(WidgetTester tester) async {
    final store = SessionStore(InMemoryKeyValueStore());
    adapter = FakeHttpAdapter((_) => FakeResponse.error(401, 'Invalid email or password'));
    await tester.pumpWidget(
      ProviderScope(
        retry: (_, _) => null,
        overrides: [
          sessionStoreProvider.overrideWithValue(store),
          dioProvider.overrideWithValue(
            buildApiDio(store: store, baseUrl: 'https://api.test/api/v1', adapter: adapter),
          ),
        ],
        child: const MaterialApp(home: LoginScreen()),
      ),
    );
  }

  testWidgets('empty form shows validation errors and sends nothing', (tester) async {
    await pumpLogin(tester);
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();

    expect(find.text('Enter your email'), findsOneWidget);
    expect(find.text('Enter your password'), findsOneWidget);
    expect(adapter.requests, isEmpty);
  });

  testWidgets('invalid email is rejected', (tester) async {
    await pumpLogin(tester);
    await tester.enterText(find.byKey(const Key('login-email')), 'not-an-email');
    await tester.enterText(find.byKey(const Key('login-password')), 'secret123');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();

    expect(find.text('Enter a valid email address'), findsOneWidget);
    expect(find.text('Enter your password'), findsNothing);
    expect(adapter.requests, isEmpty);
  });

  testWidgets('valid form submits and shows the server message on failure', (tester) async {
    await pumpLogin(tester);
    await tester.enterText(find.byKey(const Key('login-email')), ' Viewer@Example.com ');
    await tester.enterText(find.byKey(const Key('login-password')), 'wrong-password');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.path, '/api/v1/auth/login');
    expect(adapter.requests.single.data,
        {'usernameOrEmail': 'Viewer@Example.com', 'password': 'wrong-password'});
    expect(find.text('Invalid email or password'), findsOneWidget);
  });
}
