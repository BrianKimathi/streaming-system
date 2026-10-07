import '../../core/utils/phone.dart';

final _emailPattern = RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$');

String? validateEmail(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return 'Enter your email';
  if (!_emailPattern.hasMatch(v)) return 'Enter a valid email address';
  return null;
}

String? validateLoginPassword(String? value) {
  if (value == null || value.isEmpty) return 'Enter your password';
  return null;
}

String? validateNewPassword(String? value) {
  if (value == null || value.isEmpty) return 'Choose a password';
  if (value.length < 8) return 'Use at least 8 characters';
  return null;
}

String? validateOptionalPhone(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return null;
  return normalizeKenyanPhone(v) == null
      ? 'Enter a valid Safaricom/Kenyan number, e.g. 0712 345 678'
      : null;
}

String? validateRequiredPhone(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return 'Enter the M-Pesa phone number';
  return normalizeKenyanPhone(v) == null
      ? 'Enter a valid number, e.g. 0712 345 678 or +254 712 345 678'
      : null;
}
