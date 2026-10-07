# StreamX mobile (Flutter)

User-facing StreamX app for Android and iOS. Every screen loads live data from the StreamX API;
there is no mock or sample content.

## Requirements

- Flutter 3.38+ / Dart 3.10+
- Android SDK (minSdk 24) for Android builds; Xcode on macOS for iOS builds

## Run

```bash
cd apps/mobile
flutter pub get
flutter run                       # uses the production API
```

### Build-time options (`--dart-define`)

| Name           | Default                                        | Purpose                                  |
| -------------- | ---------------------------------------------- | ---------------------------------------- |
| `API_BASE_URL` | `https://streamxapi.briankimathi.dev/api/v1`   | API gateway base URL (must end in `/api/v1`) |

Examples:

```bash
# Local gateway from the Android emulator
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1

# Release APK against a staging gateway
flutter build apk --release --dart-define=API_BASE_URL=https://staging.example.com/api/v1
```

Stream URLs returned by `POST /playback/request` (`/api/v1/media/stream/...`) are resolved against the
scheme + host of `API_BASE_URL`. Plain `http://` URLs need a cleartext exception on Android 9+ / iOS ATS,
so use HTTPS outside the emulator.

## Build

```bash
flutter analyze
flutter test
flutter build apk --debug          # Android debug APK -> build/app/outputs/flutter-apk/app-debug.apk
flutter build appbundle --release  # Play Store bundle (configure a release signing key first)
flutter build ipa                  # iOS (macOS only)
```

Identifiers: Android `applicationId` and iOS bundle id are `dev.briankimathi.streamx`; display name `StreamX`.

## Features

- **Sign in / Create account** — email + password (min. 8 characters), optional Kenyan phone number.
- **Who's watching?** — profile picker, add/edit/delete profiles (built-in avatar palette, Kids toggle,
  maturity ceiling, optional 4-digit PIN), PIN prompt for locked profiles (server messages such as
  "Incorrect PIN code." and lockouts are shown as returned).
- **Home** — hero from the top trending published title, Continue Watching (progress bar, `S1:E3`
  labels, long-press to remove), Trending Now, New Movies, TV Shows and one row per genre with content.
  Pull to refresh. An empty catalog shows "No titles have been published yet".
- **Search** — debounced search across movies and TV shows, genre chips, infinite scrolling.
- **Title details** — backdrop, metadata, synopsis, Play/Resume, My List toggle (reflects the server
  response), trailer (MP4/WebM/HLS in-app, other https links such as YouTube open externally), season
  picker and episode list with per-episode progress.
- **Player** — opens in full-screen landscape (either direction) with wakelock and returns to portrait on
  exit. Controls: play/pause, ±10 s buttons, double-tap the left/right third to seek (taps accumulate:
  -20 s, +30 s), scrubber with buffered range and elapsed/remaining time, playback speed (0.5x-1.5x),
  screen lock, and for series an Episodes panel (season switcher, per-episode progress) and a Next
  Episode button. A "Next Episode" pill appears in the last 20 s of an episode; at the end a card with
  the next episode's thumbnail counts down 5 s when the profile has autoplay on. Movies end with
  "Watch again" / "Back". Playback resumes from the saved position, sends a heartbeat every 30 s,
  saves progress every 15 s and on pause/background/exit, and stops the session on exit. Errors route
  to the plans paywall (no subscription), device re-registration (device signed out), or show the
  server message.
- **Artwork and trailers** — posters, backdrops and thumbnails can be uploaded files served by the media
  service (`/api/v1/media/files/...`) or external links; trailers play in-app as progressive MP4.
- **Maturity filtering** — titles above the profile's rating are hidden; unrated titles are hidden
  from Kids profiles (see `lib/core/utils/maturity.dart`).
- **My List** — the profile's watchlist as a poster grid; long-press to remove.
- **Account** — membership card (status, renew/end date, cancel at period end / resume), plans,
  M-Pesa checkout, billing history, devices (this device marked; sign out / remove), notifications,
  profile settings (autoplay, maturity, PIN, language), change password, switch profile, sign out.

## M-Pesa checkout flow

1. **Plans** (`GET /subscriptions/plans`) compares price, resolution, streams, devices, profiles and
   downloads. Free plans activate with `POST /subscriptions/subscribe`.
2. For a paid plan, enter the M-Pesa number (prefilled from the account phone; `07…`, `01…`, `+254…`
   and `254…` are accepted and normalised to `2547…`/`2541…`).
3. `POST /billing/checkout {planId, phoneNumber}` sends the STK push. The app shows
   "Check your phone and enter your M-Pesa PIN" and polls `GET /billing/transactions/{id}` every
   3 seconds for up to 3 minutes.
4. **COMPLETED** — the subscription is reloaded and the M-Pesa receipt number is shown.
   **FAILED / CANCELLED** — the server's `errorMessage` is shown with *Try again*.
   No answer after 3 minutes — the app says so and offers *Check again*.
5. If no M-Pesa credentials are configured (admin panel: Payments (M-Pesa), or the backend `.env`),
   checkout returns 503 and the app shows "M-Pesa payments are not configured yet" as returned by
   the server.

## Structure

```
lib/
  core/       config, API client + session interceptor, errors, secure storage, session state, router, theme, utils
  data/       hand-written models (fromJson) and one repository per backend area
  features/   screens + providers: auth, profiles, devices, home, search, title, player, my_list, account
  widgets/    shared UI (artwork, posters, avatars, loading/error/empty states)
test/         model parsing, auth refresh interceptor, phone normalisation, maturity filter, login form
```

## Sessions

- Tokens (account access token, refresh token, selected profile id + profile token, device id and a
  per-install fingerprint) are kept in `flutter_secure_storage`.
- Requests carry the profile token once a profile is selected, otherwise the account token.
- On a 401 the app makes one shared `POST /auth/refresh` (with `profileId` when a profile is selected),
  stores the new tokens and retries the request once. A rejected refresh token signs the user out
  ("Your session expired"); a 404 for the profile returns to the profile picker.
- The device is registered on every sign-in/startup (`POST /devices/register`). If the plan's device
  limit is reached, a blocking screen links to Account → Devices.
