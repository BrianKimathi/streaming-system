/// Client-side maturity filtering.
///
/// The catalog stores a free-form content rating string per title ("PG-13",
/// "TV-MA", "tv_14", ...). Profiles carry a ceiling from the enum
/// G, PG, PG_13, R, NC_17, TV_Y, TV_Y7, TV_G, TV_PG, TV_14, TV_MA.
///
/// Film and TV ratings are folded onto one ordinal scale so they can be
/// compared with each other:
///
///   0  TV-Y                      (all children)
///   1  G, TV-G                   (general audience)
///   2  TV-Y7                     (children 7+)
///   3  PG, TV-PG                 (parental guidance)
///   4  PG-13, TV-14              (teens)
///   5  R                         (restricted)
///   6  NC-17, TV-MA              (adults only)
///
/// A title is visible when its level is <= the profile's level.
/// Titles with an unknown or empty rating are shown to ADULT profiles and
/// hidden from KIDS profiles. A KIDS profile without a ceiling is treated as
/// PG; an ADULT profile without a ceiling sees everything.
library;

const Map<String, int> _levels = {
  'TV_Y': 0,
  'G': 1,
  'TV_G': 1,
  'TV_Y7': 2,
  'TV_Y7_FV': 2,
  'PG': 3,
  'TV_PG': 3,
  'PG_13': 4,
  'TV_14': 4,
  'R': 5,
  'NC_17': 6,
  'TV_MA': 6,
};

const int maxMaturityLevel = 6;
const int kidsDefaultLevel = 3;

/// Profile maturity enum values in display order.
const List<String> profileMaturityValues = [
  'TV_Y',
  'G',
  'TV_G',
  'TV_Y7',
  'PG',
  'TV_PG',
  'PG_13',
  'TV_14',
  'R',
  'NC_17',
  'TV_MA',
];

/// Ceilings offered when creating a kids profile.
const List<String> kidsMaturityValues = ['TV_Y', 'G', 'TV_G', 'TV_Y7', 'PG', 'TV_PG'];

String normalizeRating(String raw) => raw
    .trim()
    .toUpperCase()
    .replaceAll(RegExp(r'[\s\-]+'), '_')
    .replaceAll(RegExp(r'_+'), '_');

/// Ordinal for a content or profile rating, or null if unknown.
int? maturityLevel(String? rating) {
  if (rating == null || rating.trim().isEmpty) return null;
  return _levels[normalizeRating(rating)];
}

/// "PG_13" -> "PG-13", "TV_MA" -> "TV-MA".
String displayRating(String rating) {
  final n = normalizeRating(rating);
  return _levels.containsKey(n) ? n.replaceAll('_', '-') : rating.trim();
}

/// Whether a title rated [contentRating] may be shown on a profile with
/// ceiling [profileRating].
bool isAllowedForProfile({
  required String? contentRating,
  required String? profileRating,
  required bool isKids,
}) {
  final ceiling = maturityLevel(profileRating) ??
      (isKids ? kidsDefaultLevel : maxMaturityLevel);
  final level = maturityLevel(contentRating);
  if (level == null) return !isKids;
  return level <= ceiling;
}
