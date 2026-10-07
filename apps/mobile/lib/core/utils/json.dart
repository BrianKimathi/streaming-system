// Tolerant JSON readers shared by the hand-written models.

typedef Json = Map<String, dynamic>;

Json asJson(Object? value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return value.map((k, v) => MapEntry('$k', v));
  throw FormatException('Expected a JSON object but got ${value.runtimeType}');
}

String? str(Object? value) {
  if (value == null) return null;
  if (value is String) return value;
  return '$value';
}

String reqStr(Json json, String key) {
  final value = str(json[key]);
  if (value == null) throw FormatException('Missing "$key"');
  return value;
}

int? intOrNull(Object? value) {
  if (value == null) return null;
  if (value is int) return value;
  if (value is num) return value.round();
  if (value is String) return int.tryParse(value) ?? double.tryParse(value)?.round();
  return null;
}

double? doubleOrNull(Object? value) {
  if (value == null) return null;
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value);
  return null;
}

bool boolOr(Object? value, [bool fallback = false]) {
  if (value is bool) return value;
  if (value is String) return value.toLowerCase() == 'true';
  if (value is num) return value != 0;
  return fallback;
}

List<T> listOf<T>(Object? value, T Function(Json json) parse) {
  if (value is! List) return const [];
  return List<T>.unmodifiable(value.whereType<Map>().map((e) => parse(asJson(e))));
}

List<String> stringList(Object? value) {
  if (value is! List) return const [];
  return List<String>.unmodifiable(value.where((e) => e != null).map((e) => '$e'));
}

/// Parses ISO-8601 timestamps. Java `LocalDateTime` values carry no offset;
/// the backend runs in UTC, so offset-less values are interpreted as UTC.
/// Also accepts Jackson array form `[y, m, d, h, min, s, nanos]`.
DateTime? dateTimeOrNull(Object? value) {
  if (value == null) return null;
  if (value is List && value.length >= 3) {
    final p = value.map((e) => intOrNull(e) ?? 0).toList();
    return DateTime.utc(
      p[0],
      p[1],
      p[2],
      p.length > 3 ? p[3] : 0,
      p.length > 4 ? p[4] : 0,
      p.length > 5 ? p[5] : 0,
      p.length > 6 ? p[6] ~/ 1000000 : 0,
    ).toLocal();
  }
  if (value is num) {
    return DateTime.fromMillisecondsSinceEpoch(value.toInt(), isUtc: true).toLocal();
  }
  if (value is! String || value.isEmpty) return null;
  final s = value.trim();
  final dateOnly = RegExp(r'^\d{4}-\d{2}-\d{2}$');
  if (dateOnly.hasMatch(s)) return DateTime.tryParse(s);
  final hasOffset = RegExp(r'(Z|[+-]\d{2}:?\d{2})$').hasMatch(s);
  final parsed = DateTime.tryParse(hasOffset ? s : '${s}Z');
  return parsed?.toLocal();
}

/// Calendar dates (`LocalDate`), kept as local midnight without shifting.
DateTime? dateOrNull(Object? value) {
  if (value == null) return null;
  if (value is List && value.length >= 3) {
    final p = value.map((e) => intOrNull(e) ?? 1).toList();
    return DateTime(p[0], p[1], p[2]);
  }
  if (value is String && value.length >= 10) {
    final d = DateTime.tryParse(value.substring(0, 10));
    if (d != null) return DateTime(d.year, d.month, d.day);
  }
  return null;
}
