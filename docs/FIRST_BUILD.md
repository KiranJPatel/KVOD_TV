# First-build guide (read this before the first sync)
The code was written without a compiler. Expect a first round of errors; they should be mechanical. Work in this order.

1. **Sync / versions** (`gradle/libs.versions.toml`): all versions are conservative pins from memory. Bump AGP/Kotlin/KSP/Compose BOM/Media3/Room/Hilt together (KSP version must match the Kotlin version). Add the Gradle wrapper (`gradle wrapper --gradle-version 8.9`).
2. **KSP (Room, Hilt)** — most likely first errors:
   - Room query validation in `core/database/Daos.kt` (the shared `CH_FILTER` string with `:merge`, `:blocked` lists and `CASE :sort`; `GroupCount` mappings).
   - Hilt graph: `ProfileRepository`, `CatalogRepository`, `SourceRepository`, `BackupRepository`, `MetadataRepository`, `Media3PlayerRepository`/`PlaybackService`. A missing `@Inject` or cycle shows up here.
3. **Kotlin/Compose compile errors** — likely spots:
   - Media3 1.5.x API names in `core/player/PlayerRepository.kt` (DrmConfiguration, SubtitleConfiguration, MediaSession builder) and `PlayerScreen.kt` (`PlayerView`).
   - Paging Compose (`itemKey`, `itemSnapshotList.items`) in `LiveScreen.kt`, `VodScreens.kt`.
   - `LocalLifecycleOwner` import (deprecated location) in `PlayerScreen.kt`; `FocusSurface` modifier-order in `core/ui/Components.kt`.
   - Overloads of `items(count = …)` for lazy list vs. grid (alias `gridItems`).
   - `data object` / `entries` need Kotlin ≥ 1.9.
4. **Unit tests** (`./gradlew :app:testDebugUnitTest`): parser, XMLTV, EPG matching, downloader, LAN entry server, backup, metadata, i18n, retry. A failing test points at a real logic bug — send the assertion output.
5. **Device tests** (`connectedDebugAndroidTest`): need Room schema JSON (`app/schemas`, created by the first successful build) for the migration test.
6. **On a Fire TV**: D-pad focus on every screen; real stream playback; Live TV preview start/stop when moving to the player and back; QR phone page; PiP (only where supported); Hindi rendering; timing/memory on a Stick.

Send errors in batches of ~20 with file names; fix order: Gradle → KSP → Kotlin → tests → device.
