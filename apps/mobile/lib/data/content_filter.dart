import '../core/utils/maturity.dart';
import 'models/catalog_models.dart';
import 'models/profile_models.dart';

/// Whether [title] may be shown to [profile] (see `core/utils/maturity.dart`
/// for the rating scale). With no profile selected nothing is filtered.
bool titleAllowed(CatalogTitle title, Profile? profile) {
  if (profile == null) return true;
  return isAllowedForProfile(
    contentRating: title.maturityRating,
    profileRating: profile.maturityRating,
    isKids: profile.isKids,
  );
}

List<CatalogTitle> filterForProfile(Iterable<CatalogTitle> titles, Profile? profile) =>
    titles.where((t) => titleAllowed(t, profile)).toList(growable: false);
