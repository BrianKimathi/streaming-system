import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/models/profile_models.dart';
import '../../data/models/subscription_models.dart';
import '../../features/account/account_screen.dart';
import '../../features/account/billing_history_screen.dart';
import '../../features/account/change_password_screen.dart';
import '../../features/account/checkout_screen.dart';
import '../../features/account/notifications_screen.dart';
import '../../features/account/plans_screen.dart';
import '../../features/account/profile_settings_screen.dart';
import '../../features/auth/login_screen.dart';
import '../../features/auth/register_screen.dart';
import '../../features/devices/device_blocked_screen.dart';
import '../../features/devices/devices_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/my_list/my_list_screen.dart';
import '../../features/player/player_args.dart';
import '../../features/player/player_screen.dart';
import '../../features/profiles/profile_editor_screen.dart';
import '../../features/profiles/profile_picker_screen.dart';
import '../../features/search/search_screen.dart';
import '../../features/shell/main_shell.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/title/title_details_screen.dart';
import '../session/session_controller.dart';

final rootNavigatorKey = GlobalKey<NavigatorState>();

const _signedOutPaths = {'/login', '/register'};
const _deviceBlockedPaths = {'/device-blocked', '/devices'};
const _profilePaths = {'/profiles', '/profiles/manage', '/profiles/edit'};
const _startupPaths = {'/splash'};

/// Pure redirect rule, exposed for tests.
String? redirectFor(SessionStage stage, String path) {
  switch (stage) {
    case SessionStage.loading:
    case SessionStage.unavailable:
      return _startupPaths.contains(path) ? null : '/splash';
    case SessionStage.signedOut:
      return _signedOutPaths.contains(path) ? null : '/login';
    case SessionStage.deviceBlocked:
      return _deviceBlockedPaths.contains(path) ? null : '/device-blocked';
    case SessionStage.choosingProfile:
      return _profilePaths.contains(path) ? null : '/profiles';
    case SessionStage.ready:
      if (_startupPaths.contains(path) ||
          _signedOutPaths.contains(path) ||
          path == '/device-blocked' ||
          _profilePaths.contains(path)) {
        return '/home';
      }
      return null;
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  final session = ValueNotifier<SessionState>(ref.read(sessionControllerProvider));
  ref.listen<SessionState>(sessionControllerProvider, (_, next) => session.value = next);

  final router = GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: '/splash',
    refreshListenable: session,
    redirect: (context, state) => redirectFor(session.value.stage, state.matchedLocation),
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
      GoRoute(path: '/device-blocked', builder: (_, _) => const DeviceBlockedScreen()),
      GoRoute(path: '/devices', builder: (_, _) => const DevicesScreen()),
      GoRoute(path: '/profiles', builder: (_, _) => const ProfilePickerScreen()),
      GoRoute(
        path: '/profiles/manage',
        builder: (_, _) => const ProfilePickerScreen(manage: true),
      ),
      GoRoute(
        path: '/profiles/edit',
        builder: (_, state) => ProfileEditorScreen(existing: state.extra as Profile?),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => MainShell(shell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/home', builder: (_, _) => const HomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/search', builder: (_, _) => const SearchScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/my-list', builder: (_, _) => const MyListScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/account', builder: (_, _) => const AccountScreen()),
          ]),
        ],
      ),
      GoRoute(
        path: '/title/:kind/:id',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, state) => TitleDetailsScreen(
          kind: state.pathParameters['kind']!,
          id: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        path: '/play',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, state) {
          final args = state.extra;
          return args is PlayerArgs
              ? PlayerScreen(args: args)
              : const _MissingArgsScreen();
        },
      ),
      GoRoute(
        path: '/plans',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, state) => PlansScreen(reason: state.extra as String?),
      ),
      GoRoute(
        path: '/checkout',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, state) {
          final plan = state.extra;
          return plan is Plan ? CheckoutScreen(plan: plan) : const _MissingArgsScreen();
        },
      ),
      GoRoute(
        path: '/billing-history',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, _) => const BillingHistoryScreen(),
      ),
      GoRoute(
        path: '/notifications',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, _) => const NotificationsScreen(),
      ),
      GoRoute(
        path: '/profile-settings',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, _) => const ProfileSettingsScreen(),
      ),
      GoRoute(
        path: '/change-password',
        parentNavigatorKey: rootNavigatorKey,
        builder: (_, _) => const ChangePasswordScreen(),
      ),
    ],
  );
  ref.onDispose(() {
    router.dispose();
    session.dispose();
  });
  return router;
});

class _MissingArgsScreen extends StatelessWidget {
  const _MissingArgsScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: const Center(child: Text('This page could not be opened.')),
    );
  }
}
