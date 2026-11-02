# Known limitations

1. **Not compiled, not run.** No Gradle/SDK here. Expect possible compile fixes, especially Media3 `@UnstableApi` signatures (`DefaultHlsExtractorFactory`, `AnalyticsListener` overrides) and Room schema export. Run `./gradlew assembleDebug testDebugUnitTest` first. Room will generate `schemas/…/3.json`; no 2→3 `MigrationTestHelper` test exists yet.
2. Every capability is NOT TESTED; no FULL status anywhere; no soak, thermal or leak results.
3. Not implemented: catch-up/time-shift UI, RTMP/SRT/RIST/RTP, FFmpeg software decoding (VC-1, DivX, WMA, ALAC, APE, DSD), subtitle/audio delay, styled ASS, VobSub/Teletext, tunneling, licensed Dolby/DTS decode, tone mapping.
4. Dolby Atmos/TrueHD/DTS-HD/DTS:X depend on the receiver and HDMI link; unverifiable without that hardware.
5. Duplicate hiding runs as a correlated SQL subquery; very large lists (100k+) may need a materialised flag. Names that differ beyond quality tags (spelling, country prefixes) are treated as different channels on purpose. Existing channels get keys in a background pass after the update, during which they are not de-duplicated.
6. Quality-rank order is tuned for a 1080p TV (4K ranks below untagged).
7. Media3 not upgraded.
