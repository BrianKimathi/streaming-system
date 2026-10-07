import '../../core/api/api_client.dart';
import '../../core/utils/json.dart';
import '../models/auth_models.dart';

class AuthRepository {
  AuthRepository(this._api);

  final ApiClient _api;

  Future<AuthResult> login(String email, String password) => _api.post(
        '/auth/login',
        body: {'usernameOrEmail': email.trim(), 'password': password},
        parse: (d) => AuthResult.fromJson(asJson(d)),
      );

  Future<AuthResult> register({
    required String email,
    required String password,
    String? phoneNumber,
  }) =>
      _api.post(
        '/auth/register',
        body: {
          'email': email.trim().toLowerCase(),
          'password': password,
          if (phoneNumber != null && phoneNumber.isNotEmpty) 'phoneNumber': phoneNumber,
        },
        parse: (d) => AuthResult.fromJson(asJson(d)),
      );

  Future<void> logout(String? refreshToken) => _api.post(
        '/auth/logout',
        body: {'refreshToken': ?refreshToken},
        parse: ApiClient.ignore,
      );

  Future<Account> me() => _api.get('/auth/me', parse: (d) => Account.fromJson(asJson(d)));

  Future<AuthResult> changePassword(String currentPassword, String newPassword) => _api.post(
        '/auth/change-password',
        body: {'currentPassword': currentPassword, 'newPassword': newPassword},
        domain401: true,
        parse: (d) => AuthResult.fromJson(asJson(d)),
      );
}
