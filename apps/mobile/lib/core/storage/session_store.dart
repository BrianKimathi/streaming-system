import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

abstract class KeyValueStore {
  Future<String?> read(String key);
  Future<void> write(String key, String? value);
}

class SecureKeyValueStore implements KeyValueStore {
  SecureKeyValueStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String? value) => value == null
      ? _storage.delete(key: key)
      : _storage.write(key: key, value: value);
}

/// Holds the credentials of the signed-in account in memory and mirrors every
/// change to secure storage. The API interceptor reads tokens from here.
class SessionStore {
  SessionStore(this._kv);

  final KeyValueStore _kv;

  static const _kAccountToken = 'sx.accountAccessToken';
  static const _kRefreshToken = 'sx.refreshToken';
  static const _kAccountId = 'sx.accountId';
  static const _kEmail = 'sx.email';
  static const _kProfileId = 'sx.profileId';
  static const _kProfileToken = 'sx.profileAccessToken';
  static const _kDeviceId = 'sx.deviceId';
  static const _kFingerprint = 'sx.installFingerprint';

  String? accountAccessToken;
  String? refreshToken;
  String? accountId;
  String? email;
  String? profileId;
  String? profileAccessToken;
  String? deviceId;

  bool get hasSession => refreshToken != null && accountId != null;
  bool get hasProfile => profileId != null && profileAccessToken != null;

  /// The bearer token for API calls: the profile token once a profile is
  /// selected, otherwise the account token.
  String? get activeAccessToken =>
      profileId != null ? profileAccessToken : accountAccessToken;

  Future<void> load() async {
    accountAccessToken = await _kv.read(_kAccountToken);
    refreshToken = await _kv.read(_kRefreshToken);
    accountId = await _kv.read(_kAccountId);
    email = await _kv.read(_kEmail);
    profileId = await _kv.read(_kProfileId);
    profileAccessToken = await _kv.read(_kProfileToken);
    deviceId = await _kv.read(_kDeviceId);
    if (profileId != null && profileAccessToken == null) {
      await clearProfile();
    }
  }

  Future<void> saveSignIn({
    required String accountId,
    required String email,
    required String accessToken,
    required String refreshToken,
  }) async {
    this.accountId = accountId;
    this.email = email;
    accountAccessToken = accessToken;
    this.refreshToken = refreshToken;
    profileId = null;
    profileAccessToken = null;
    await _kv.write(_kAccountId, accountId);
    await _kv.write(_kEmail, email);
    await _kv.write(_kAccountToken, accessToken);
    await _kv.write(_kRefreshToken, refreshToken);
    await _kv.write(_kProfileId, null);
    await _kv.write(_kProfileToken, null);
  }

  /// Stores the result of `POST /auth/refresh`. When the refresh was made for
  /// a profile, the returned access token is a profile token.
  Future<void> saveRefreshed({
    required String accessToken,
    required String? refreshToken,
    required bool forProfile,
  }) async {
    if (forProfile) {
      profileAccessToken = accessToken;
      await _kv.write(_kProfileToken, accessToken);
    } else {
      accountAccessToken = accessToken;
      await _kv.write(_kAccountToken, accessToken);
    }
    if (refreshToken != null && refreshToken.isNotEmpty) {
      this.refreshToken = refreshToken;
      await _kv.write(_kRefreshToken, refreshToken);
    }
  }

  Future<void> saveRefreshToken(String refreshToken) async {
    if (refreshToken.isEmpty) return;
    this.refreshToken = refreshToken;
    await _kv.write(_kRefreshToken, refreshToken);
  }

  Future<void> saveAccountTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    accountAccessToken = accessToken;
    this.refreshToken = refreshToken;
    await _kv.write(_kAccountToken, accessToken);
    await _kv.write(_kRefreshToken, refreshToken);
  }

  Future<void> saveProfile(String profileId, String profileAccessToken) async {
    this.profileId = profileId;
    this.profileAccessToken = profileAccessToken;
    await _kv.write(_kProfileId, profileId);
    await _kv.write(_kProfileToken, profileAccessToken);
  }

  Future<void> clearProfile() async {
    profileId = null;
    profileAccessToken = null;
    await _kv.write(_kProfileId, null);
    await _kv.write(_kProfileToken, null);
  }

  Future<void> saveDeviceId(String? id) async {
    deviceId = id;
    await _kv.write(_kDeviceId, id);
  }

  /// Clears everything except the install fingerprint, which identifies this
  /// installation to the device service across sign-ins.
  Future<void> clear() async {
    accountAccessToken = null;
    refreshToken = null;
    accountId = null;
    email = null;
    profileId = null;
    profileAccessToken = null;
    deviceId = null;
    for (final key in [
      _kAccountToken,
      _kRefreshToken,
      _kAccountId,
      _kEmail,
      _kProfileId,
      _kProfileToken,
      _kDeviceId,
    ]) {
      await _kv.write(key, null);
    }
  }

  Future<String> installFingerprint() async {
    final existing = await _kv.read(_kFingerprint);
    if (existing != null && existing.isNotEmpty) return existing;
    final created = const Uuid().v4();
    await _kv.write(_kFingerprint, created);
    return created;
  }
}
