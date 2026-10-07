import 'package:flutter_test/flutter_test.dart';
import 'package:streamx/core/utils/json.dart';
import 'package:streamx/data/models/auth_models.dart';
import 'package:streamx/data/models/catalog_models.dart';
import 'package:streamx/data/models/misc_models.dart';
import 'package:streamx/data/models/profile_models.dart';
import 'package:streamx/data/models/subscription_models.dart';

const genreJson = {'id': 'g-1', 'name': 'Drama', 'slug': 'drama'};

const movieJson = {
  'id': 'm-1',
  'title': 'The Long Road',
  'synopsis': 'A journey.',
  'releaseDate': '2024-05-17',
  'runtimeMinutes': 118,
  'maturityRating': 'PG-13',
  'posterUrl': 'https://cdn.example/p.jpg',
  'backdropUrl': '',
  'trailerUrl': null,
  'status': 'PUBLISHED',
  'genres': [genreJson],
  'createdAt': '2025-01-02T10:15:30.123456',
};

const showJson = {
  'id': 's-1',
  'title': 'Harbour Lights',
  'synopsis': 'A series.',
  'releaseDate': '2023-09-01',
  'maturityRating': 'TV-14',
  'posterUrl': null,
  'backdropUrl': null,
  'trailerUrl': 'https://cdn.example/trailer.m3u8',
  'status': 'PUBLISHED',
  'genres': [genreJson],
  'seasonsCount': 2,
  'createdAt': '2025-01-02T10:15:30Z',
};

Map<String, dynamic> episode(String id, int season, int number) => {
      'id': id,
      'seasonId': 'season-$season',
      'tvShowId': 's-1',
      'seasonNumber': season,
      'episodeNumber': number,
      'title': 'Episode $number',
      'synopsis': null,
      'runtimeMinutes': 45,
      'releaseDate': '2023-09-0$number',
      'thumbnailUrl': number == 1 ? 'https://cdn.example/e1.jpg' : null,
    };

const planJson = {
  'id': 'plan-1',
  'name': 'Standard',
  'description': 'Full HD on two screens',
  'price': 899.0,
  'currency': 'KES',
  'billingInterval': 'MONTHLY',
  'version': 3,
  'active': true,
  'maxProfiles': 5,
  'maxRegisteredDevices': 4,
  'maxConcurrentStreams': 2,
  'maxResolution': 'FHD_1080P',
  'hdrEnabled': false,
  'audioQuality': 'STEREO',
  'downloadsEnabled': true,
  'maxDownloadDevices': 2,
  'kidsProfilesEnabled': true,
};

void main() {
  group('auth', () {
    test('AuthResponse', () {
      final r = AuthResult.fromJson({
        'accountId': 'acc-1',
        'email': 'viewer@example.com',
        'phoneNumber': '+254712345678',
        'emailVerified': false,
        'phoneVerified': false,
        'roles': ['USER'],
        'accessToken': 'a',
        'refreshToken': 'r',
      });
      expect(r.accountId, 'acc-1');
      expect(r.roles, ['USER']);
      expect(r.accessToken, 'a');
      expect(r.refreshToken, 'r');
      expect(r.phoneNumber, '+254712345678');
    });

    test('AuthResponse without tokens is rejected', () {
      expect(() => AuthResult.fromJson({'accountId': 'acc-1'}), throwsFormatException);
    });

    test('AccountResponse', () {
      final a = Account.fromJson({
        'accountId': 'acc-1',
        'email': 'viewer@example.com',
        'phoneNumber': null,
        'emailVerified': true,
        'phoneVerified': false,
        'status': 'ACTIVE',
        'roles': ['USER'],
        'createdAt': '2025-03-01T08:00:00',
      });
      expect(a.emailVerified, isTrue);
      expect(a.status, 'ACTIVE');
      expect(a.createdAt, DateTime.utc(2025, 3, 1, 8).toLocal());
    });
  });

  group('profiles', () {
    const profileJson = {
      'id': 'p-1',
      'accountId': 'acc-1',
      'name': 'Kim',
      'avatarUrl': 'avatar:7',
      'type': 'KIDS',
      'maturityRating': 'TV_Y7',
      'language': 'sw',
      'preferredAudio': 'en',
      'preferredSubtitle': null,
      'autoplayNext': false,
      'pinProtected': true,
      'createdAt': '2025-03-01T08:00:00',
    };

    test('ProfileResponse', () {
      final p = Profile.fromJson(profileJson);
      expect(p.isKids, isTrue);
      expect(p.avatarIndex, 7);
      expect(p.maturityRating, 'TV_Y7');
      expect(p.autoplayNext, isFalse);
      expect(p.pinProtected, isTrue);
      expect(p.language, 'sw');
    });

    test('SelectProfileResponse', () {
      final s = SelectProfileResult.fromJson({'profileAccessToken': 'pt', 'profile': profileJson});
      expect(s.profileAccessToken, 'pt');
      expect(s.profile.id, 'p-1');
    });

    test('WatchlistItemResponse', () {
      final w = WatchlistItem.fromJson({'titleId': 's-1', 'titleType': 'SERIES', 'addedAt': '2025-03-01T08:00:00'});
      expect(w.isSeries, isTrue);
      expect(w.addedAt, isNotNull);
    });
  });

  group('catalog', () {
    test('GenreResponse', () {
      final g = Genre.fromJson(genreJson);
      expect(g.name, 'Drama');
      expect(g.slug, 'drama');
    });

    test('MovieResponse', () {
      final m = CatalogTitle.movieFromJson(movieJson);
      expect(m.kind, TitleKind.movie);
      expect(m.year, 2024);
      expect(m.runtimeMinutes, 118);
      expect(m.backdropUrl, isNull, reason: 'empty artwork strings are treated as missing');
      expect(m.genres.single.id, 'g-1');
      expect(m.createdAt, DateTime.utc(2025, 1, 2, 10, 15, 30, 123, 456).toLocal());
    });

    test('TvShowResponse', () {
      final s = CatalogTitle.showFromJson(showJson);
      expect(s.isSeries, isTrue);
      expect(s.seasonsCount, 2);
      expect(s.trailerUrl, endsWith('.m3u8'));
    });

    test('Page<MovieResponse>', () {
      final page = PageResult.fromJson({
        'content': [movieJson],
        'totalElements': 21,
        'totalPages': 2,
        'number': 0,
        'size': 20,
        'last': false,
      }, CatalogTitle.movieFromJson);
      expect(page.content.single.id, 'm-1');
      expect(page.last, isFalse);
      expect(page.totalElements, 21);
    });

    test('Page without "last" derives it from number/totalPages', () {
      final page = PageResult.fromJson(
          {'content': [], 'totalPages': 1, 'number': 0}, CatalogTitle.movieFromJson);
      expect(page.last, isTrue);
    });

    test('TvShowDetailResponse orders seasons and episodes and finds the next episode', () {
      final detail = TvShowDetail.fromJson({
        ...showJson,
        'seasons': [
          {
            'id': 'season-2',
            'tvShowId': 's-1',
            'seasonNumber': 2,
            'title': null,
            'synopsis': null,
            'releaseDate': null,
            'posterUrl': null,
            'episodes': [episode('e-2-1', 2, 1)],
          },
          {
            'id': 'season-1',
            'tvShowId': 's-1',
            'seasonNumber': 1,
            'title': 'Beginnings',
            'episodes': [episode('e-1-2', 1, 2), episode('e-1-1', 1, 1)],
          },
        ],
      });
      expect(detail.seasons.map((s) => s.seasonNumber), [1, 2]);
      expect(detail.seasons.first.displayName, 'Beginnings');
      expect(detail.seasons.last.displayName, 'Season 2');
      expect(detail.seasons.first.episodes.map((e) => e.id), ['e-1-1', 'e-1-2']);
      expect(detail.firstEpisode!.id, 'e-1-1');
      expect(detail.nextEpisodeAfter('e-1-1')!.id, 'e-1-2');
      expect(detail.nextEpisodeAfter('e-1-2')!.id, 'e-2-1');
      expect(detail.nextEpisodeAfter('e-2-1'), isNull);
      expect(detail.episodeById('e-1-2')!.label, 'S1:E2');
    });

    test('EpisodeResponse', () {
      final e = Episode.fromJson(episode('e-1-1', 1, 1));
      expect(e.label, 'S1:E1');
      expect(e.thumbnailUrl, 'https://cdn.example/e1.jpg');
      expect(e.runtimeMinutes, 45);
    });

    test('lookup result', () {
      final l = LookupResult.fromJson({
        'movies': [movieJson],
        'tvShows': [showJson],
        'episodes': [episode('e-1-1', 1, 1)],
      });
      expect(l.titlesById.keys, containsAll(['m-1', 's-1']));
      expect(l.episodesById['e-1-1']!.tvShowId, 's-1');
    });
  });

  group('subscriptions & billing', () {
    test('PlanResponse', () {
      final p = Plan.fromJson(planJson);
      expect(p.price, 899);
      expect(p.isFree, isFalse);
      expect(p.maxConcurrentStreams, 2);
      expect(p.downloadsEnabled, isTrue);
      expect(Plan.fromJson({...planJson, 'price': 0}).isFree, isTrue);
    });

    test('SubscriptionResponse', () {
      final s = Subscription.fromJson({
        'id': 'sub-1',
        'accountId': 'acc-1',
        'plan': planJson,
        'status': 'ACTIVE',
        'currentPeriodStart': '2026-10-01T00:00:00',
        'currentPeriodEnd': '2026-11-01T00:00:00',
        'cancelAtPeriodEnd': true,
        'entitlements': {
          'subscriptionId': 'sub-1',
          'status': 'ACTIVE',
          'maxProfiles': 5,
          'maxRegisteredDevices': 4,
          'maxConcurrentStreams': 2,
          'maxResolution': 'FHD_1080P',
          'hdrEnabled': false,
          'audioQuality': 'STEREO',
          'downloadsEnabled': true,
          'maxDownloadDevices': 2,
          'kidsProfilesEnabled': true,
        },
      });
      expect(s.isActive, isTrue);
      expect(s.cancelAtPeriodEnd, isTrue);
      expect(s.plan!.name, 'Standard');
      expect(s.entitlements!.maxRegisteredDevices, 4);
      expect(s.currentPeriodEnd, DateTime.utc(2026, 11, 1).toLocal());
    });

    test('PaymentTransactionResponse', () {
      final t = PaymentTransaction.fromJson({
        'id': 'tx-1',
        'accountId': 'acc-1',
        'subscriptionId': null,
        'planId': 'plan-1',
        'planName': 'Standard',
        'amount': 899,
        'currency': 'KES',
        'status': 'PENDING',
        'paymentMethod': 'MPESA',
        'phoneNumber': '2547****5678',
        'externalTransactionId': null,
        'errorMessage': null,
        'createdAt': '2026-10-07T12:00:00',
        'updatedAt': '2026-10-07T12:00:05',
      });
      expect(t.isPending, isTrue);
      expect(t.amount, 899);
      expect(t.phoneNumber, '2547****5678');
      final done = PaymentTransaction.fromJson({'id': 'tx-1', 'status': 'COMPLETED', 'externalTransactionId': 'SJ12ABC'});
      expect(done.isCompleted, isTrue);
      expect(PaymentTransaction.fromJson({'id': 'x', 'status': 'CANCELLED'}).isFailed, isTrue);
    });
  });

  group('devices, playback, history, trending, notifications', () {
    test('DeviceResponse', () {
      final d = Device.fromJson({
        'id': 'd-1',
        'accountId': 'acc-1',
        'deviceFingerprint': 'fp',
        'deviceName': 'Samsung SM-A515F',
        'deviceType': 'PHONE',
        'platform': 'android',
        'appVersion': '1.0.0+1',
        'status': 'REVOKED',
        'registeredAt': '2026-10-01T09:00:00',
        'lastSeenAt': null,
      });
      expect(d.isActive, isFalse);
      expect(d.deviceType, 'PHONE');
      expect(d.lastSeenAt, isNull);
    });

    test('PlaybackAuthResponse', () {
      final g = PlaybackGrant.fromJson({
        'sessionId': 'sess-1',
        'contentId': 'e-1-1',
        'streamUrl': '/api/v1/media/stream/tok/e-1-1/master.m3u8',
        'status': 'ACTIVE',
        'expiresAt': '2026-10-07T18:00:00',
        'durationSeconds': 2700,
      });
      expect(g.sessionId, 'sess-1');
      expect(g.durationSeconds, 2700);
      expect(g.streamUrl, startsWith('/api/v1/media/stream/'));
    });

    test('WatchProgressResponse', () {
      final w = WatchProgress.fromJson({
        'id': 'w-1',
        'profileId': 'p-1',
        'contentId': 'e-1-2',
        'titleId': 's-1',
        'titleType': 'SERIES',
        'positionSeconds': 600,
        'durationSeconds': 2400,
        'percentage': 25.0,
        'completed': false,
        'lastWatchedAt': [2026, 10, 7, 12, 30, 0],
      });
      expect(w.fraction, closeTo(0.25, 1e-9));
      expect(w.titleId, 's-1');
      expect(w.lastWatchedAt, DateTime.utc(2026, 10, 7, 12, 30).toLocal());
    });

    test('TrendingItemResponseDto', () {
      final t = TrendingItem.fromJson({
        'contentId': 'm-1',
        'title': 'The Long Road',
        'contentType': 'MOVIE',
        'views1h': 3,
        'views6h': 10,
        'completions24h': 2,
        'likes24h': 0,
        'velocityScore': 4.5,
        'updatedAt': '2026-10-07T12:00:00Z',
      });
      expect(t.contentId, 'm-1');
      expect(t.views6h, 10);
      expect(t.velocityScore, 4.5);
    });

    test('NotificationResponseDto', () {
      final n = AppNotification.fromJson({
        'id': 'n-1',
        'accountId': 'acc-1',
        'recipient': 'acc-1',
        'channel': 'IN_APP',
        'template': 'PAYMENT_SUCCESS',
        'subject': 'Payment received',
        'body': 'Your Standard plan is active.',
        'status': 'SENT',
        'failureReason': null,
        'createdAt': '2026-10-07T12:00:00',
      });
      expect(n.subject, 'Payment received');
      expect(n.channel, 'IN_APP');
    });
  });

  group('json helpers', () {
    test('LocalDateTime without offset is treated as UTC', () {
      expect(dateTimeOrNull('2026-10-07T12:00:00'), DateTime.utc(2026, 10, 7, 12).toLocal());
      expect(dateTimeOrNull('2026-10-07T12:00:00+03:00'), DateTime.utc(2026, 10, 7, 9).toLocal());
      expect(dateTimeOrNull(null), isNull);
      expect(dateTimeOrNull(''), isNull);
    });

    test('LocalDate keeps the calendar day', () {
      expect(dateOrNull('2024-05-17'), DateTime(2024, 5, 17));
      expect(dateOrNull([2024, 5, 17]), DateTime(2024, 5, 17));
    });
  });
}
