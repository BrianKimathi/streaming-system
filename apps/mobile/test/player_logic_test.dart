import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:streamx/core/config/app_config.dart';
import 'package:streamx/data/models/catalog_models.dart';
import 'package:streamx/data/models/misc_models.dart';
import 'package:streamx/features/player/player_logic.dart';
import 'package:streamx/features/player/widgets/end_overlays.dart';
import 'package:streamx/features/player/widgets/episodes_panel.dart';

void main() {
  group('tap zones', () {
    test('thirds of the surface', () {
      expect(tapZoneFor(10, 900), TapZone.left);
      expect(tapZoneFor(299, 900), TapZone.left);
      expect(tapZoneFor(450, 900), TapZone.center);
      expect(tapZoneFor(601, 900), TapZone.right);
      expect(tapZoneFor(5, 0), TapZone.center);
    });
  });

  group('DoubleTapSeekAccumulator', () {
    final t0 = DateTime(2026, 10, 7, 20);
    DateTime at(int ms) => t0.add(Duration(milliseconds: ms));

    test('a single side tap is not a seek', () {
      final acc = DoubleTapSeekAccumulator();
      expect(acc.registerTap(TapZone.right, at(0)), isNull);
      expect(acc.totalSeconds, 0);
    });

    test('a double tap seeks ten seconds and further taps accumulate', () {
      final acc = DoubleTapSeekAccumulator();
      expect(acc.registerTap(TapZone.right, at(0)), isNull);
      expect(acc.registerTap(TapZone.right, at(200)), 10);
      expect(acc.totalSeconds, 10);
      expect(acc.registerTap(TapZone.right, at(600)), 10);
      expect(acc.registerTap(TapZone.right, at(1100)), 10);
      expect(acc.totalSeconds, 30);
      expect(seekLabel(acc.totalSeconds), '+30s');
    });

    test('backward runs are negative', () {
      final acc = DoubleTapSeekAccumulator();
      acc.registerTap(TapZone.left, at(0));
      expect(acc.registerTap(TapZone.left, at(250)), -10);
      expect(acc.registerTap(TapZone.left, at(500)), -10);
      expect(acc.side, SeekSide.backward);
      expect(acc.totalSeconds, -20);
      expect(seekLabel(acc.totalSeconds), '−20s');
    });

    test('taps too far apart are two single taps', () {
      final acc = DoubleTapSeekAccumulator();
      expect(acc.registerTap(TapZone.right, at(0)), isNull);
      expect(acc.registerTap(TapZone.right, at(400)), isNull);
    });

    test('a tap on the other side does not complete a double tap', () {
      final acc = DoubleTapSeekAccumulator();
      expect(acc.registerTap(TapZone.left, at(0)), isNull);
      expect(acc.registerTap(TapZone.right, at(100)), isNull);
    });

    test('switching sides during a run restarts the count', () {
      final acc = DoubleTapSeekAccumulator();
      acc.registerTap(TapZone.right, at(0));
      acc.registerTap(TapZone.right, at(200));
      acc.registerTap(TapZone.right, at(500));
      expect(acc.totalSeconds, 20);
      expect(acc.registerTap(TapZone.left, at(700)), -10);
      expect(acc.totalSeconds, -10);
    });

    test('the run ends after the continue window', () {
      final acc = DoubleTapSeekAccumulator();
      acc.registerTap(TapZone.right, at(0));
      acc.registerTap(TapZone.right, at(200));
      expect(acc.isSeeking(at(900)), isTrue);
      expect(acc.isSeeking(at(1100)), isFalse);
      expect(acc.registerTap(TapZone.right, at(1100)), isNull, reason: 'a new double tap is needed');
      expect(acc.registerTap(TapZone.right, at(1250)), 10);
      expect(acc.totalSeconds, 10);
    });

    test('centre taps never seek', () {
      final acc = DoubleTapSeekAccumulator();
      expect(acc.registerTap(TapZone.center, at(0)), isNull);
      expect(acc.registerTap(TapZone.center, at(100)), isNull);
    });
  });

  group('seeking', () {
    test('clampSeek stays within the video', () {
      const d = Duration(minutes: 10);
      expect(clampSeek(const Duration(seconds: 5), const Duration(seconds: -10), d), Duration.zero);
      expect(clampSeek(const Duration(seconds: 595), const Duration(seconds: 10), d), d);
      expect(clampSeek(const Duration(seconds: 60), const Duration(seconds: 10), d), const Duration(seconds: 70));
      expect(clampSeek(const Duration(seconds: 60), const Duration(seconds: 30), Duration.zero),
          const Duration(seconds: 90), reason: 'unknown duration has no upper bound');
    });

    test('speed labels', () {
      expect(playbackSpeeds, [0.5, 0.75, 1.0, 1.25, 1.5]);
      expect(speedLabel(1.0), '1×');
      expect(speedLabel(0.75), '0.75×');
      expect(speedLabel(1.5), '1.5×');
    });
  });

  group('next episode pill', () {
    bool show(int positionS, int durationS, {bool hasNext = true}) => shouldShowNextPill(
          position: Duration(seconds: positionS),
          duration: Duration(seconds: durationS),
          hasNext: hasNext,
        );

    test('appears in the last 20 seconds of a normal episode', () {
      expect(show(2679, 2700), isFalse);
      expect(show(2680, 2700), isTrue);
      expect(show(2699, 2700), isTrue);
    });

    test('short episodes use the last 3 %', () {
      // 5 minutes: 3 % = 9 s.
      expect(show(290, 300), isFalse);
      expect(show(291, 300), isTrue);
    });

    test('hidden without a next episode, at the end, or with unknown duration', () {
      expect(show(2690, 2700, hasNext: false), isFalse);
      expect(show(2700, 2700), isFalse);
      expect(show(10, 0), isFalse);
    });
  });

  group('media URLs', () {
    const origin = 'https://streamxapi.briankimathi.dev';

    test('absolute media-service and external URLs are kept', () {
      const file = 'https://streamxapi.briankimathi.dev/api/v1/media/files/abc/poster.jpg';
      expect(AppConfig.resolveMediaUrl(file, origin: origin), file);
      expect(AppConfig.resolveMediaUrl('  https://image.tmdb.org/t/p/w500/x.jpg ', origin: origin),
          'https://image.tmdb.org/t/p/w500/x.jpg');
      expect(AppConfig.resolveMediaUrl('http://cdn.example/a.png', origin: origin), 'http://cdn.example/a.png');
    });

    test('relative /api paths resolve against the API origin', () {
      expect(AppConfig.resolveMediaUrl('/api/v1/media/files/abc/trailer.mp4', origin: origin),
          '$origin/api/v1/media/files/abc/trailer.mp4');
      expect(AppConfig.resolveMediaUrl('api/v1/media/files/abc/b.jpg', origin: origin),
          '$origin/api/v1/media/files/abc/b.jpg');
      expect(AppConfig.resolveMediaUrl('/api/v1/media/files/x/y.jpg', origin: 'http://10.0.2.2:8080'),
          'http://10.0.2.2:8080/api/v1/media/files/x/y.jpg');
    });

    test('protocol-relative URLs become https; blanks and other schemes are dropped', () {
      expect(AppConfig.resolveMediaUrl('//cdn.example/p.jpg', origin: origin), 'https://cdn.example/p.jpg');
      expect(AppConfig.resolveMediaUrl(null), isNull);
      expect(AppConfig.resolveMediaUrl('   '), isNull);
      expect(AppConfig.resolveMediaUrl('avatar:3'), isNull);
      expect(AppConfig.resolveMediaUrl('ftp://host/file.jpg'), isNull);
      expect(AppConfig.resolveMediaUrl('https://'), isNull);
    });

    test('catalog models expose absolute artwork URLs', () {
      final movie = CatalogTitle.movieFromJson({
        'id': 'm-1',
        'title': 'Uploaded',
        'posterUrl': '/api/v1/media/files/f1/poster.jpg',
        'backdropUrl': 'https://streamxapi.briankimathi.dev/api/v1/media/files/f2/backdrop.webp',
        'trailerUrl': '/api/v1/media/files/f3/trailer.mp4',
      });
      expect(movie.posterUrl, '${AppConfig.apiOrigin}/api/v1/media/files/f1/poster.jpg');
      expect(movie.backdropUrl, 'https://streamxapi.briankimathi.dev/api/v1/media/files/f2/backdrop.webp');
      expect(movie.trailerUrl, '${AppConfig.apiOrigin}/api/v1/media/files/f3/trailer.mp4');
      final ep = Episode.fromJson({'id': 'e-1', 'title': 'Pilot', 'thumbnailUrl': '/api/v1/media/files/f4/t.png'});
      expect(ep.thumbnailUrl, '${AppConfig.apiOrigin}/api/v1/media/files/f4/t.png');
    });

    test('trailer links: direct files play in-app, web pages open externally', () {
      expect(AppConfig.isDirectVideoUrl('https://streamxapi.briankimathi.dev/api/v1/media/files/f3/trailer.mp4'), isTrue);
      expect(AppConfig.isDirectVideoUrl('https://streamxapi.briankimathi.dev/api/v1/media/files/f3/clip'), isTrue);
      expect(AppConfig.isDirectVideoUrl('https://cdn.example/t.webm?sig=1'), isTrue);
      expect(AppConfig.isDirectVideoUrl('https://cdn.example/t/master.m3u8'), isTrue);
      expect(AppConfig.isDirectVideoUrl('https://www.youtube.com/watch?v=abc'), isFalse);
      expect(AppConfig.isHlsUrl('https://cdn.example/t.mp4'), isFalse, reason: 'progressive MP4 gets no HLS hint');
      expect(AppConfig.isHlsUrl('/api/v1/media/stream/tok/c/master.m3u8'), isTrue);
    });
  });

  group('player widgets', () {
    const next = Episode(id: 'e-2', title: 'The Return', seasonNumber: 1, episodeNumber: 2);

    Widget host(Widget child) => MaterialApp(home: Scaffold(body: Center(child: child)));

    testWidgets('next episode card shows the countdown only when autoplaying', (tester) async {
      var played = 0;
      await tester.pumpWidget(host(NextEpisodeCard(
        episode: next,
        fallbackImageUrl: null,
        countdown: 5,
        onPlayNow: () => played++,
        onCancel: () {},
      )));
      expect(find.text('Next episode in 5'), findsOneWidget);
      expect(find.text('S1:E2  The Return'), findsOneWidget);
      await tester.tap(find.text('Play now'));
      expect(played, 1);

      await tester.pumpWidget(host(NextEpisodeCard(
        episode: next,
        fallbackImageUrl: null,
        countdown: null,
        onPlayNow: () {},
        onCancel: () {},
      )));
      expect(find.text('Next episode'), findsOneWidget);
      expect(find.textContaining('in 5'), findsNothing);
    });

    testWidgets('episodes panel opens on the current season and switches seasons', (tester) async {
      tester.view.physicalSize = const Size(2400, 1080);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);

      final show = TvShowDetail(
        show: const CatalogTitle(id: 's-1', kind: TitleKind.series, title: 'Harbour Lights'),
        seasons: const [
          Season(id: 'season-1', seasonNumber: 1, episodes: [
            Episode(id: 'e-1-1', title: 'Arrival', seasonNumber: 1, episodeNumber: 1),
          ]),
          Season(id: 'season-2', seasonNumber: 2, episodes: [
            Episode(id: 'e-2-1', title: 'Storm', seasonNumber: 2, episodeNumber: 1),
            Episode(id: 'e-2-2', title: 'Calm', seasonNumber: 2, episodeNumber: 2),
          ]),
        ],
      );
      Episode? selected;
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 440,
            child: EpisodesPanel(
              show: show,
              currentEpisodeId: 'e-2-1',
              progress: const {
                'e-2-2': WatchProgress(contentId: 'e-2-2', positionSeconds: 60, durationSeconds: 120),
              },
              progressLoading: false,
              progressError: null,
              currentFraction: 0.5,
              onSelect: (e) => selected = e,
              onClose: () {},
            ),
          ),
        ),
      ));

      expect(find.text('Season 2'), findsOneWidget);
      expect(find.text('1. Storm'), findsOneWidget);
      expect(find.text('Now playing'), findsOneWidget);
      expect(find.byType(LinearProgressIndicator), findsNWidgets(2));

      await tester.tap(find.text('1. Storm'));
      expect(selected, isNull, reason: 'the playing episode is not restarted');
      await tester.tap(find.text('2. Calm'));
      expect(selected?.id, 'e-2-2');

      await tester.tap(find.text('Season 2'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Season 1').last);
      await tester.pumpAndSettle();
      expect(find.text('1. Arrival'), findsOneWidget);
      expect(find.text('Now playing'), findsNothing);
    });
  });
}
