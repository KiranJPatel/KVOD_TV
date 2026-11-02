# Dependency matrix and rationale
Versions live in `gradle/libs.versions.toml`. They are conservative pins chosen from memory, **not verified against today's releases** — bump to the latest stable set in Android Studio (Tools → AGP Upgrade Assistant / Version Catalog hints) and re-run the tests.

| Area | Library | Pinned | Why / Fire OS notes |
|---|---|---|---|
| Build | AGP / Kotlin / KSP | 8.7.3 / 2.0.21 / 2.0.21-1.0.28 | KSP instead of kapt (faster); Compose compiler via Kotlin plugin |
| UI | Compose BOM, Material 3, icons-extended | 2024.12.01 | Plain Compose focus APIs; `androidx.tv:tv-material` deliberately **not** used to avoid a pre-1.0-style API surface that was not verifiable here |
| Navigation | navigation-compose | 2.8.5 | Works without Google services |
| Playback | Media3 exoplayer, hls, dash, rtsp, ui, okhttp datasource | 1.5.1 | No Play Services dependency; DRM only via Media3 (no DRM configured) |
| DI | Hilt (+ androidx.hilt work/navigation) | 2.52 / 1.2.0 | Compile-time DI, no reflection |
| DB | Room | 2.6.1 | `@Upsert`, exported schema in `app/schemas` |
| Prefs | DataStore Preferences | 1.1.1 | Non-secret settings only |
| Background | WorkManager | 2.10.0 | Uses its own scheduler (no GCM dependency) — works on Fire OS |
| Network | OkHttp | 4.12.0 | Manual redirect validation |
| Serialization | kotlinx-serialization-json | 1.7.3 | Secrets blob, Xtream JSON, headers |
| Images | Coil | 2.7.0 | Bounded memory/disk caches |
| Java time | desugar_jdk_libs | 2.1.3 | `java.time` on API 25 |
| Tests | JUnit4, coroutines-test, MockWebServer, kxml2/xmlpull (JVM XML parser) | see catalog | Android `XmlPullParser` is a stub in JVM tests, so kxml2 is used there |

Platform levels: `minSdk 25` (Fire OS 6/7/8, Android TV 7.1+), `compileSdk 35`, `targetSdk 34`. **Verify the current Amazon Appstore target-API requirement before submission** and raise `targetSdk` if needed (re-test cleartext, foreground-service and exact-alarm behaviour).
No Google Play Services, Firebase, Cast or Google TV-only libraries are used.
