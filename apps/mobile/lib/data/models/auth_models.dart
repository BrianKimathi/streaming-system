import '../../core/utils/json.dart';

class AuthResult {
  const AuthResult({
    required this.accountId,
    required this.email,
    required this.accessToken,
    required this.refreshToken,
    this.phoneNumber,
    this.emailVerified = false,
    this.phoneVerified = false,
    this.roles = const [],
  });

  final String accountId;
  final String email;
  final String? phoneNumber;
  final bool emailVerified;
  final bool phoneVerified;
  final List<String> roles;
  final String accessToken;
  final String refreshToken;

  factory AuthResult.fromJson(Json json) => AuthResult(
        accountId: reqStr(json, 'accountId'),
        email: str(json['email']) ?? '',
        phoneNumber: str(json['phoneNumber']),
        emailVerified: boolOr(json['emailVerified']),
        phoneVerified: boolOr(json['phoneVerified']),
        roles: stringList(json['roles']),
        accessToken: reqStr(json, 'accessToken'),
        refreshToken: reqStr(json, 'refreshToken'),
      );
}

class Account {
  const Account({
    required this.accountId,
    required this.email,
    this.phoneNumber,
    this.emailVerified = false,
    this.phoneVerified = false,
    this.status,
    this.roles = const [],
    this.createdAt,
  });

  final String accountId;
  final String email;
  final String? phoneNumber;
  final bool emailVerified;
  final bool phoneVerified;
  final String? status;
  final List<String> roles;
  final DateTime? createdAt;

  factory Account.fromJson(Json json) => Account(
        accountId: reqStr(json, 'accountId'),
        email: str(json['email']) ?? '',
        phoneNumber: str(json['phoneNumber']),
        emailVerified: boolOr(json['emailVerified']),
        phoneVerified: boolOr(json['phoneVerified']),
        status: str(json['status']),
        roles: stringList(json['roles']),
        createdAt: dateTimeOrNull(json['createdAt']),
      );
}
