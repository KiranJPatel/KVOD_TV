# Playback architecture

Unchanged: one `PlayerRepository` (Media3 ExoPlayer singleton), one MediaSession, `PlaybackService`. A second engine (FFmpeg) was evaluated and **not** added: no maintained Maven artefact for Media3's FFmpeg extension (must be built from source with the NDK), LGPL/GPL obligations, ABI size cost, and no way to build/verify here. The seam for it is `DefaultRenderersFactory` extension mode; nothing else needs to change.

New/changed components
- `MediaHttp` / `CachingDns`: media client.
- `FailoverController`, `StreamHealth`, `StreamAlt`: pure failover state.
- `Media3PlayerRepository`: `startCandidate`, watchdog, `attachAlternates`, `promotePreview`; HLS built with tuned `HlsMediaSource.Factory`, everything else through `DefaultMediaSourceFactory` with tuned `DefaultExtractorsFactory`.
- `PlaybackDiagnostics` (AnalyticsListener): actual decoders, dropped frames, audio underruns, bandwidth.
- `DeviceCapabilityDetector`: report from platform APIs; JSON saved to `files/reports/DEVICE_CAPABILITIES.json`.
- `ChannelNaming`, DB v3 (`dupKey`, `qualityRank`), `ChannelKeyBackfill`.

Audio routing: Media3 chooses passthrough vs decode from the sink's reported encodings; the app adds no virtual surround and shows no Atmos/DTS:X badge.
