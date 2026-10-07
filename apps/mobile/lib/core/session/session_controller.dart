import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/models/auth_models.dart';
import '../../data/models/misc_models.dart';
import '../../data/models/profile_models.dart';
import '../api/api_exception.dart';
import '../device/device_identity.dart';
import '../providers.dart';

enum SessionStage {
  /// Restoring the stored session / registering the device.
  loading,

  /// Startup could not reach the server; [SessionState.message] explains.
  unavailable,
  signedOut,

  /// Device registration was refused (e.g. device limit reached).
  deviceBlocked,
  choosingProfile,
  ready,
}

class SessionState {
  const SessionState({
    required this.stage,
    this.email,
    this.accountId,
    this.profile,
    this.deviceId,
    this.message,
  });

  final SessionStage stage;
  final String? email;
  final String? accountId;
  final Profile? profile;
  final String? deviceId;

  /// Notice for the current stage: "Your session expired", the device limit
  /// message, a startup failure, etc.
  final String? message;

  SessionState copyWith({
    SessionStage? stage,
    String? email,
    String? accountId,
    Profile? profile,
    bool clearProfile = false,
    String? deviceId,
    String? message,
    bool clearMessage = false,
  }) =>
      SessionState(
        stage: stage ?? this.stage,
        email: email ?? this.email,
        accountId: accountId ?? this.accountId,
        profile: clearProfile ? null : (profile ?? this.profile),
        deviceId: deviceId ?? this.deviceId,
        message: clearMessage ? null : (message ?? this.message),
      );
}

final deviceIdentityLoaderProvider =
    Provider<Future<DeviceIdentity> Function()>((ref) => DeviceIdentity.current);

final sessionControllerProvider =
    NotifierProvider<SessionController, SessionState>(SessionController.new);

/// Changes whenever the active profile (or the parts of it that affect what
/// content is visible) changes. Profile-scoped data providers watch this.
final profileKeyProvider = Provider<(String?, String?, bool)>((ref) {
  return ref.watch(sessionControllerProvider.select(
    (s) => (s.profile?.id, s.profile?.maturityRating, s.profile?.isKids ?? false),
  ));
});

final currentProfileProvider =
    Provider<Profile?>((ref) => ref.watch(sessionControllerProvider.select((s) => s.profile)));

class SessionController extends Notifier<SessionState> {
  static const sessionExpiredMessage = 'Your session expired. Please sign in again.';

  bool _bootstrapping = false;

  @override
  SessionState build() => const SessionState(stage: SessionStage.loading);

  /// Restores the stored session at app start.
  Future<void> bootstrap() async {
    if (_bootstrapping) return;
    _bootstrapping = true;
    try {
      final store = ref.read(sessionStoreProvider);
      if (!store.hasSession) {
        state = const SessionState(stage: SessionStage.signedOut);
        return;
      }
      state = SessionState(
        stage: SessionStage.loading,
        email: store.email,
        accountId: store.accountId,
        deviceId: store.deviceId,
      );
      await _continueAfterSignIn();
    } finally {
      _bootstrapping = false;
    }
  }

  Future<void> retryStartup() => bootstrap();

  Future<void> login(String email, String password) async {
    final result = await ref.read(authRepositoryProvider).login(email, password);
    await _acceptSignIn(result);
  }

  Future<void> register({
    required String email,
    required String password,
    String? phoneNumber,
  }) async {
    final result = await ref.read(authRepositoryProvider).register(
          email: email,
          password: password,
          phoneNumber: phoneNumber,
        );
    await _acceptSignIn(result);
  }

  Future<void> _acceptSignIn(AuthResult result) async {
    final store = ref.read(sessionStoreProvider);
    await store.saveSignIn(
      accountId: result.accountId,
      email: result.email,
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
    );
    // The stage stays on the sign-in screen (which shows its own progress)
    // until device registration decides where to go next.
    state = SessionState(
      stage: state.stage,
      email: result.email,
      accountId: result.accountId,
    );
    await _continueAfterSignIn();
  }

  Future<void> _continueAfterSignIn() async {
    final store = ref.read(sessionStoreProvider);
    try {
      final device = await _registerDevice();
      state = state.copyWith(deviceId: device.id);
    } on ApiException catch (e) {
      if (!store.hasSession) return; // handleSessionExpired already ran
      if (isDeviceLimitError(e) || e.isForbidden || e.statusCode == 400) {
        state = state.copyWith(stage: SessionStage.deviceBlocked, message: e.message);
      } else {
        state = state.copyWith(stage: SessionStage.unavailable, message: e.message);
      }
      return;
    }

    if (!store.hasProfile) {
      state = state.copyWith(
        stage: SessionStage.choosingProfile,
        clearProfile: true,
        clearMessage: true,
      );
      return;
    }

    try {
      final profiles = await ref.read(profileRepositoryProvider).list();
      if (!store.hasSession) return;
      final match = profiles.where((p) => p.id == store.profileId).toList();
      if (match.isEmpty) {
        await store.clearProfile();
        state = state.copyWith(
          stage: SessionStage.choosingProfile,
          clearProfile: true,
          clearMessage: true,
        );
      } else {
        state = state.copyWith(
          stage: SessionStage.ready,
          profile: match.first,
          clearMessage: true,
        );
      }
    } on ApiException catch (e) {
      if (!store.hasSession) return;
      if (!store.hasProfile) {
        state = state.copyWith(stage: SessionStage.choosingProfile, clearProfile: true);
        return;
      }
      state = state.copyWith(stage: SessionStage.unavailable, message: e.message);
    }
  }

  static bool isDeviceLimitError(ApiException e) =>
      e.message.toLowerCase().contains('device limit');

  Future<Device> _registerDevice() async {
    final store = ref.read(sessionStoreProvider);
    final fingerprint = await store.installFingerprint();
    final identity = await ref.read(deviceIdentityLoaderProvider)();
    final device = await ref.read(deviceRepositoryProvider).register(
          fingerprint: fingerprint,
          name: identity.name,
          type: identity.type,
          platform: identity.platform,
          appVersion: identity.appVersion,
        );
    await store.saveDeviceId(device.id);
    return device;
  }

  /// Re-registers this device after playback reported it signed out. On a
  /// device-limit refusal the app moves to the blocking device screen.
  Future<void> reRegisterDevice() async {
    try {
      final device = await _registerDevice();
      state = state.copyWith(deviceId: device.id);
    } on ApiException catch (e) {
      if (isDeviceLimitError(e)) {
        state = state.copyWith(stage: SessionStage.deviceBlocked, message: e.message);
      }
      rethrow;
    }
  }

  /// From the blocking device screen, after the user freed a slot.
  Future<void> retryDeviceRegistration() async {
    state = state.copyWith(stage: SessionStage.loading, clearMessage: true);
    await _continueAfterSignIn();
  }

  Future<void> selectProfile(Profile profile, {String? pin}) async {
    final result = await ref.read(profileRepositoryProvider).select(profile.id, pin: pin);
    await ref.read(sessionStoreProvider).saveProfile(result.profile.id, result.profileAccessToken);
    state = state.copyWith(
      stage: SessionStage.ready,
      profile: result.profile,
      clearMessage: true,
    );
  }

  /// Applies a server-confirmed update of the current profile.
  void profileUpdated(Profile profile) {
    if (state.profile?.id == profile.id) state = state.copyWith(profile: profile);
  }

  Future<void> switchProfile() async {
    await ref.read(sessionStoreProvider).clearProfile();
    state = state.copyWith(
      stage: SessionStage.choosingProfile,
      clearProfile: true,
      clearMessage: true,
    );
  }

  /// A profile was deleted from Manage profiles.
  Future<void> profileDeleted(String profileId) async {
    final store = ref.read(sessionStoreProvider);
    if (store.profileId == profileId) await store.clearProfile();
    if (state.profile?.id == profileId) {
      state = state.copyWith(stage: SessionStage.choosingProfile, clearProfile: true);
    }
  }

  Future<void> passwordChanged(AuthResult result) async {
    await ref.read(sessionStoreProvider).saveAccountTokens(
          accessToken: result.accessToken,
          refreshToken: result.refreshToken,
        );
  }

  Future<void> logout() async {
    final store = ref.read(sessionStoreProvider);
    final refreshToken = store.refreshToken;
    try {
      await ref.read(authRepositoryProvider).logout(refreshToken);
    } on ApiException {
      // The local session is cleared regardless; the token expires server-side.
    }
    await store.clear();
    state = const SessionState(stage: SessionStage.signedOut);
  }

  /// Called by the API interceptor after the refresh token was rejected.
  void handleSessionExpired() {
    state = const SessionState(stage: SessionStage.signedOut, message: sessionExpiredMessage);
  }

  /// Called by the API interceptor when the selected profile no longer exists.
  void handleProfileGone(String message) {
    state = state.copyWith(
      stage: SessionStage.choosingProfile,
      clearProfile: true,
      message: 'That profile is no longer available. Choose another one.',
    );
  }

  void clearMessage() {
    if (state.message != null) state = state.copyWith(clearMessage: true);
  }
}
