# Implementation changelog (unbuilt, see KNOWN_LIMITATIONS)

- Duplicate channels: DB v3 (+`dupKey`, `qualityRank`, migration 2→3, backfill), SQL hiding in list/ids/group counts/search, `alternatesOf`, merge switch removed.
- Playback: `MediaHttp`, `DefaultDataSource`, per-request headers, TS/HLS fast start, CEA-608, decoder fallback, failover + watchdog + health memory, preview promotion, first-picture logging, `PlaybackDiagnostics`.
- Start-up: play before DB extras; batched channel lookup.
- Buffer profiles retuned; `BEST` quality; `udp://` accepted; `.sup` subtitle mapping.
- Capability report (Settings); "Trying backup stream" text.
- Tests: `ChannelNamingTest`, `FailoverTest`.
