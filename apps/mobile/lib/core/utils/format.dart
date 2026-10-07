import 'package:intl/intl.dart';

String formatDate(DateTime? date) =>
    date == null ? '—' : DateFormat.yMMMd().format(date);

String formatDateTime(DateTime? date) =>
    date == null ? '—' : DateFormat.yMMMd().add_jm().format(date);

String formatMoney(num? amount, String? currency) {
  if (amount == null) return '—';
  final code = (currency == null || currency.isEmpty) ? '' : '$currency ';
  final hasCents = amount != amount.roundToDouble();
  final formatted = NumberFormat.decimalPatternDigits(
    decimalDigits: hasCents ? 2 : 0,
  ).format(amount);
  return '$code$formatted';
}

String formatRuntime(int? minutes) {
  if (minutes == null || minutes <= 0) return '';
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return '${m}m';
  return m == 0 ? '${h}h' : '${h}h ${m}m';
}

/// 75 -> "1:15", 3725 -> "1:02:05".
String formatClock(Duration d) {
  final negative = d.isNegative;
  final total = d.abs().inSeconds;
  final h = total ~/ 3600;
  final m = (total % 3600) ~/ 60;
  final s = total % 60;
  final mm = h > 0 ? m.toString().padLeft(2, '0') : '$m';
  final body = h > 0 ? '$h:$mm:${s.toString().padLeft(2, '0')}' : '$mm:${s.toString().padLeft(2, '0')}';
  return negative ? '-$body' : body;
}

String humanizeEnum(String? value) {
  if (value == null || value.isEmpty) return '—';
  return value
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1).toLowerCase()}')
      .join(' ');
}

String resolutionLabel(String? value) {
  switch (value) {
    case 'SD_720P':
      return '720p HD';
    case 'FHD_1080P':
      return '1080p Full HD';
    case 'UHD_4K':
      return '4K Ultra HD';
    default:
      return humanizeEnum(value);
  }
}

String intervalLabel(String? value) {
  switch (value) {
    case 'MONTHLY':
      return 'month';
    case 'YEARLY':
      return 'year';
    default:
      return (value ?? '').toLowerCase();
  }
}
