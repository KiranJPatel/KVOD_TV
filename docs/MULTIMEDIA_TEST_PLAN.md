# Multimedia test plan

Automated (JVM, written but **not executed here**): `ChannelNamingTest`, `FailoverTest` (failover order, health memory, buffer-profile validity, DNS cache). Naming expectations were simulated with equivalent regex logic and agree.

Required on device (none done): per-codec/container matrix with open assets (Blender/Big Buck Bunny, Tears of Steel, Jellyfin and Kodi test suites, Apple/DASH-IF public test streams); HDR clips on an HDR TV; 5.1/7.1 channel-ID file through an AVR for passthrough; HLS/DASH/TS public streams with airplane-mode interruption; multicast on a lab network; 30 min/1 h/3 h/6 h soak with memory and thermal sampling; release-leak check (MediaCodec/AudioTrack). Per test record: ID, asset, container, codecs, resolution, fps, decoder (from `PlaybackDiagnostics`), HW/SW, route, result, duration, dropped frames, buffering, errors, device, Android, date. Results: PASS / CONDITIONAL PASS / FAIL / NOT TESTED; a successful `prepare` alone is never a PASS.
Duplicate hiding: import a playlist with FHD/HD/SD copies and the same channel from two sources; list shows one; kill the primary URL and confirm switch within the profile timeout.
