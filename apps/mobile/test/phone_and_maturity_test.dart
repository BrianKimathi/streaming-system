import 'package:flutter_test/flutter_test.dart';
import 'package:streamx/core/config/app_config.dart';
import 'package:streamx/core/router/app_router.dart';
import 'package:streamx/core/session/session_controller.dart';
import 'package:streamx/core/utils/maturity.dart';
import 'package:streamx/core/utils/phone.dart';
import 'package:streamx/data/content_filter.dart';
import 'package:streamx/data/models/catalog_models.dart';
import 'package:streamx/data/models/profile_models.dart';

void main() {
  group('normalizeKenyanPhone', () {
    test('accepts the common Kenyan formats', () {
      expect(normalizeKenyanPhone('0712345678'), '254712345678');
      expect(normalizeKenyanPhone('0112345678'), '254112345678');
      expect(normalizeKenyanPhone('+254712345678'), '254712345678');
      expect(normalizeKenyanPhone('254712345678'), '254712345678');
      expect(normalizeKenyanPhone('712345678'), '254712345678');
      expect(normalizeKenyanPhone(' 0712 345-678 '), '254712345678');
      expect(normalizeKenyanPhone('+254 (712) 345 678'), '254712345678');
    });

    test('rejects invalid numbers', () {
      for (final bad in [null, '', '12345', '0812345678', '07123456789', '+255712345678', '07a2345678', '2540712345678']) {
        expect(normalizeKenyanPhone(bad), isNull, reason: '$bad');
      }
    });

    test('formats for display', () {
      expect(formatKenyanPhone('254712345678'), '0712 345 678');
    });
  });

  group('maturity', () {
    test('ratings tolerate case and separators', () {
      expect(maturityLevel('PG-13'), maturityLevel('pg_13'));
      expect(maturityLevel('tv-ma'), maturityLevel('TV_MA'));
      expect(maturityLevel('TV Y7'), maturityLevel('TV_Y7'));
      expect(maturityLevel('NR'), isNull);
      expect(maturityLevel(''), isNull);
      expect(displayRating('PG_13'), 'PG-13');
    });

    test('ordering across film and TV ratings', () {
      expect(maturityLevel('TV-Y')! < maturityLevel('G')!, isTrue);
      expect(maturityLevel('G'), maturityLevel('TV-G'));
      expect(maturityLevel('PG'), maturityLevel('TV-PG'));
      expect(maturityLevel('PG-13'), maturityLevel('TV-14'));
      expect(maturityLevel('R')! < maturityLevel('NC-17')!, isTrue);
      expect(maturityLevel('NC-17'), maturityLevel('TV-MA'));
    });

    test('profile ceilings', () {
      bool allowed(String? content, String? profile, {bool kids = false}) =>
          isAllowedForProfile(contentRating: content, profileRating: profile, isKids: kids);

      expect(allowed('PG', 'PG_13'), isTrue);
      expect(allowed('R', 'PG_13'), isFalse);
      expect(allowed('TV-14', 'PG_13'), isTrue);
      expect(allowed('TV-MA', 'TV_MA'), isTrue);
      expect(allowed('TV-Y7', 'TV_Y', kids: true), isFalse);
      expect(allowed('G', 'TV_Y7', kids: true), isTrue);
    });

    test('unknown ratings: shown to adults, hidden from kids', () {
      expect(isAllowedForProfile(contentRating: null, profileRating: 'TV_MA', isKids: false), isTrue);
      expect(isAllowedForProfile(contentRating: 'Unrated', profileRating: 'PG', isKids: false), isTrue);
      expect(isAllowedForProfile(contentRating: null, profileRating: 'PG', isKids: true), isFalse);
      expect(isAllowedForProfile(contentRating: '', profileRating: 'TV_Y7', isKids: true), isFalse);
    });

    test('profiles without a ceiling', () {
      expect(isAllowedForProfile(contentRating: 'NC-17', profileRating: null, isKids: false), isTrue);
      expect(isAllowedForProfile(contentRating: 'PG', profileRating: null, isKids: true), isTrue);
      expect(isAllowedForProfile(contentRating: 'PG-13', profileRating: null, isKids: true), isFalse);
    });

    test('filterForProfile', () {
      const kids = Profile(id: 'k', name: 'Kids', type: 'KIDS', maturityRating: 'PG');
      const titles = [
        CatalogTitle(id: '1', kind: TitleKind.movie, title: 'A', maturityRating: 'G'),
        CatalogTitle(id: '2', kind: TitleKind.movie, title: 'B', maturityRating: 'R'),
        CatalogTitle(id: '3', kind: TitleKind.series, title: 'C', maturityRating: 'tv-pg'),
        CatalogTitle(id: '4', kind: TitleKind.series, title: 'D'),
      ];
      expect(filterForProfile(titles, kids).map((t) => t.id), ['1', '3']);
      expect(filterForProfile(titles, null), hasLength(4));
    });
  });

  group('config & routing', () {
    test('stream URLs resolve against the API origin', () {
      expect(AppConfig.originOf('https://streamxapi.briankimathi.dev/api/v1'),
          'https://streamxapi.briankimathi.dev');
      expect(AppConfig.originOf('http://10.0.2.2:8080/api/v1'), 'http://10.0.2.2:8080');
    });

    test('redirects follow the session stage', () {
      expect(redirectFor(SessionStage.loading, '/home'), '/splash');
      expect(redirectFor(SessionStage.signedOut, '/home'), '/login');
      expect(redirectFor(SessionStage.signedOut, '/register'), isNull);
      expect(redirectFor(SessionStage.deviceBlocked, '/home'), '/device-blocked');
      expect(redirectFor(SessionStage.deviceBlocked, '/devices'), isNull);
      expect(redirectFor(SessionStage.choosingProfile, '/home'), '/profiles');
      expect(redirectFor(SessionStage.choosingProfile, '/profiles/edit'), isNull);
      expect(redirectFor(SessionStage.ready, '/login'), '/home');
      expect(redirectFor(SessionStage.ready, '/profiles'), '/home');
      expect(redirectFor(SessionStage.ready, '/title/movie/x'), isNull);
    });
  });
}
