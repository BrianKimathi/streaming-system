import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/providers.dart';
import 'core/storage/session_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  final store = SessionStore(SecureKeyValueStore());
  await store.load();

  runApp(
    ProviderScope(
      // Failed loads surface the server message with a Retry button instead
      // of being retried silently in the background.
      retry: (_, _) => null,
      overrides: [sessionStoreProvider.overrideWithValue(store)],
      child: const StreamXApp(),
    ),
  );
}
