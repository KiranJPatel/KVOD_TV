# Amazon Appstore release-readiness checklist
Verify each item against the current Amazon developer documentation — requirements change.
- [ ] Project builds in Android Studio; `assembleRelease` signed with your own upload keystore (not committed).
- [ ] All unit tests green; `connectedDebugAndroidTest` run on a Fire TV Stick (and a 4K Max/Cube if available) and on an Android TV emulator.
- [ ] Manual D-pad pass on every screen with the stock Fire TV remote: no focus traps, Back always exits dialogs/screens, Home button leaves the app.
- [ ] Test devices/Fire OS versions recorded (Fire OS 6, 7, 8). Fire OS 5 excluded in listing.
- [ ] `targetSdk` meets the current Appstore requirement; run Amazon's app-compatibility/“Vega”/Fire OS checks if applicable.
- [ ] Manifest: no touchscreen requirement, banner 320×180 and launcher icon replaced with final artwork, no unneeded permissions (only INTERNET, ACCESS_NETWORK_STATE).
- [ ] No Google Play Services / Firebase dependency (verified by `./gradlew :app:dependencies`).
- [ ] Privacy policy URL (no analytics, local-only storage, user-supplied sources) and data-safety answers prepared.
- [ ] Content policy: app ships **no** channels/streams; listing states that users must have rights to the content they add; remove the sample data wording if it could be mistaken for real channels.
- [ ] Memory/startup profiling on a Fire TV Stick (cold start target ≈2 s, channel change ≈2 s) with the numbers recorded in this folder.
- [ ] Accessibility: text-size setting checked at 130 %; contrast checked on all four themes; screen-reader labels on icon-only controls.
- [ ] Screenshots 1920×1080 for each section; short description and tagline "Your Entertainment. Your Way."
- [ ] Open-source licence notices complete (Settings → About) and bundled in the listing if required.
- [ ] Versioning: bump `versionCode`/`versionName`; keep the R8 mapping file for crash analysis.
