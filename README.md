# KVOD TV — Your Entertainment. Your Way.

KVOD TV is a player for **Amazon Fire TV** and compatible **Android TV** devices. Kotlin, Jetpack Compose (Material 3, 10-foot UI), Media3 ExoPlayer, Room, DataStore, WorkManager, Hilt, OkHttp, Coil.
It plays content **you are authorised to access**: nothing is bundled, and no channel is shown until you add a source (or explicitly load the fictitious demo data).

> **What is new in 1.1.0:** see `docs/CHANGES_V1_1.md` (subtitle styles, volume / brightness, random play, playlists, six themes, KVOD TV branding). Themes: `docs/THEMES.md`.
>
> **Verification status (please read).** This project was produced in an environment without the Android SDK, Gradle or network access.
> **Nothing has been compiled, run or tested on a device.** The code was written carefully and statically checked for balanced syntax only.
> Expect a first-build round of compile fixes (dependency versions, a few API signatures). See `docs/FEATURES.md` for what is implemented vs. untested.

## Requirements
- Android Studio (latest stable), JDK 17, Android SDK 35 platform.
- Device: Fire TV (Fire OS 6+, Android 7.1+) or Android TV (API 25+). Fire OS 5 devices (API 22) are not supported.

## Build and run
```bash
# 1. Open the folder in Android Studio and let it sync (it provisions the Gradle wrapper).
#    Or, with Gradle 8.9 installed:  gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug            # debug APK  → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest        # JVM unit tests (parser, XMLTV, EPG matching, downloader, security, retry…)
./gradlew :app:connectedDebugAndroidTest# device tests (import stability, profile isolation, parental filtering, progress, PIN, migrations, D-pad focus, empty/error panels) — needs a device/emulator
./gradlew :app:assembleRelease          # needs keystore.properties (see below)
```
Install on a Fire TV: enable *Developer options → ADB debugging*, then `adb connect <fire-tv-ip>` and `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Android TV emulator: create a *Television (1080p)* AVD with API 34.

### Release signing
Create `keystore.properties` next to `settings.gradle.kts` (it is git-ignored; never commit it):
```
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## First run
1. Welcome screen → **Add Your First Playlist** (URL), *Use a file*, *Xtream account*, or **Try sample data (demo)**.
2. Sample data (`app/src/main/assets/sample/sample_playlist.m3u` + a synthetic guide generated at import) uses fictitious channels with non-functional `example.invalid` addresses. Channels list and the guide works; playback shows the error panel — that is expected and exercises the error UI.
3. `docs/sample/malformed_playlist.m3u` demonstrates the import diagnostics (unterminated quote, missing URL, unsafe scheme, duplicates).

## Remote controls
| Key | Browsing | Player (overlay hidden) | Player (overlay visible) |
|---|---|---|---|
| D-pad | Moves focus; Left from content edge enters the rail | Up/Down: previous/next channel (live, if enabled); Left/Right: seek −10 s/+30 s (VOD) | Moves between controls |
| Select | Activate | Show overlay | Activate control |
| Back | Close dialog → Home → exit app | Hide overlay → (confirm) → leave player | Hide overlay |
| Play/Pause, FF/RW, CH+/CH−, Last | — | Supported | Supported |

## Architecture (single Gradle module, packages mirror the suggested modules)
```
app/src/main/kotlin/com/iptv/firetv/
  app/            navigation shell, rail, MainViewModel (start route, inactivity lock)
  core/model      domain models            core/common   text normalisation, redaction, errors, diagnostics log
  core/parser     M3U + XMLTV parsers     core/network  URL validation, bounded downloader, Xtream client
  core/security   Keystore AES-GCM, PIN   core/database  Room entities/DAOs (schema v1, exported)
  core/epg        EPG channel matcher     core/player   PlayerRepository (Media3), retry policy, error mapping
  core/ui         theme (4 themes), design system components, focus handling
  data/           repositories (settings, profiles, catalog, user data, sources, import, EPG, stream resolver)
  feature/        home, live, player, epg, vod, favorites, search, sources, profiles, settings
  work/           WorkManager periodic refresh      di/  Hilt modules
```
*Why one module:* the project could not be compiled here, and a multi-module build multiplies the places a first-build error can hide. Package boundaries follow the suggested module split, so promoting a package to a Gradle module is mechanical.

Data flow: Compose renders immutable state → ViewModels (StateFlow) → repositories → Room / network / player. No parsing, network or DB work happens in composables.

## Design
See `docs/DESIGN.md` for the approved "Midnight Cinema" design (split hero, live preview in Live TV, focus rule, tokens) and what is implemented.

## Themes and typography
Settings → Appearance: **Midnight** (spec palette #050B14/#0B1422/#111F30, accent #1688FF, glow #42B8FF), **Material dark**, **Material gray**, **Light**; five accent colours; text size 90–130 %.
Typography is tuned per theme (min 16 sp, 10-foot scale): dark themes use Regular body weight and slightly open tracking to limit halation; Material gray uses a taller line height; Light uses Medium body weight and tighter tracking for contrast on bright panels. System sans-serif is used (no font files are bundled; drop a font family into `Theme.kt` to change it).

## Optional features and permissions
Notification/transport controls (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`), Picture-in-picture, voice search (system recognizer), phone-assisted URL entry (local one-shot web page), external subtitles, TMDB metadata (your own key) and backup/restore are described in `docs/FEATURES.md` and `docs/LIMITATIONS.md`.

## Security and privacy summary
- Source URLs, usernames, passwords and user-agents are stored only as an AES-256-GCM blob (key in Android Keystore); DataStore holds non-sensitive settings only.
- Logs and the in-app diagnostics view are redacted (`Redactor`); Xtream stream URLs are resolved at play time from the encrypted credentials instead of being stored per item. M3U stream URLs are stored as given in the app-private database (`allowBackup=false`).
- HTTPS by default; HTTP is allowed but warned about. Downloads are size-bounded, time-limited, retried a bounded number of times, and every redirect hop is re-validated (max 5, no https→http).
- XMLTV parsing never processes DOCTYPE/external entities. Only http/https/rtsp stream schemes are accepted.
- Parental restrictions are applied in SQL and repositories (lists, search, Continue Watching, direct id lookups), not only in the UI.
- No analytics or crash-reporting SDKs. No credentials in source, `BuildConfig` or resources.
