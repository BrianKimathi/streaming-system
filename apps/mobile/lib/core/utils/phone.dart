/// Normalises a Kenyan mobile number to the M-Pesa format `2547XXXXXXXX` or
/// `2541XXXXXXXX`. Accepts `07…`, `01…`, `7…`, `1…`, `+254…` and `254…`, with
/// spaces, dashes or parentheses. Returns null when the number is invalid.
String? normalizeKenyanPhone(String? input) {
  if (input == null) return null;
  var digits = input.trim().replaceAll(RegExp(r'[\s\-().]'), '');
  if (digits.startsWith('+')) digits = digits.substring(1);
  if (!RegExp(r'^\d+$').hasMatch(digits)) return null;

  String local;
  if (digits.startsWith('254') && digits.length == 12) {
    local = digits.substring(3);
  } else if (digits.startsWith('0') && digits.length == 10) {
    local = digits.substring(1);
  } else if (digits.length == 9) {
    local = digits;
  } else {
    return null;
  }
  if (!RegExp(r'^[71]\d{8}$').hasMatch(local)) return null;
  return '254$local';
}

/// "254712345678" -> "0712 345 678" for display.
String formatKenyanPhone(String normalized) {
  if (normalized.length != 12 || !normalized.startsWith('254')) return normalized;
  final local = '0${normalized.substring(3)}';
  return '${local.substring(0, 4)} ${local.substring(4, 7)} ${local.substring(7)}';
}
