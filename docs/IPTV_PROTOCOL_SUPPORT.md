# IPTV protocol support

| Protocol | Status | Evidence / condition |
|---|---|---|
| HLS (TS, fMP4/CMAF, AES-128, multiple audio/text renditions, LL-HLS) | PARTIAL / NOT TESTED | Media3 HLS module. Fast-start TS flags and CEA-608 exposure added. LL-HLS depends on server |
| MPEG-DASH (static/dynamic, SegmentTemplate/Timeline, Widevine) | NOT TESTED | Media3 DASH module; MPEG-TS-in-DASH unsupported by Media3 |
| MPEG-TS over HTTP(S) | NOT TESTED | Progressive source + TS extractor with new flags |
| UDP multicast MPEG-TS | NOT IMPLEMENTED→CONDITIONAL | `udp://` now accepted and routed to `UdpDataSource`; needs a network that delivers multicast; untested. `udp://@host:port` normalised |
| RTSP | PARTIAL | Media3 RTSP, typical H.264/AAC only |
| RTP, RTMP, SRT, RIST, WebSocket | NOT IMPLEMENTED | RTMP needs `media3-datasource-rtmp` (third-party client lib); SRT/RIST need native stacks. Not added: cannot verify offline |
| Icecast/Shoutcast | NOT TESTED | Progressive audio via Media3 |
| Smooth Streaming | UNSUPPORTED | Module not included |
| Catch-up / time-shift | NOT IMPLEMENTED | Provider-specific; needs EPG UI work. Plan: parse `catchup`/`catchup-source`, Xtream `timeshift` URLs, expose "watch from start" in EPG |
| Live DVR window seek | PARTIAL | Whatever the HLS/DASH playlist window allows (ExoPlayer); no extra UI |

## Loading / start-up logic (this release)
1. Playback request is issued before favourites, zap list, alternates and EPG are loaded.
2. Preview→fullscreen reuses the running stream.
3. Redirect-following media client, DNS cache, 8 s connect timeout, connection pool.
4. Buffer profiles retuned (Balanced: start 1.0 s, rebuffer 2.5 s, max 40 s).
5. Time to first picture is logged per tune-in (Settings → diagnostics log).

## Duplicate channels
Shown once. Key = tvg-id (when meaningful) or name with quality/codec tags removed; region/language words are kept. Best copy = FHD > HD > untagged > 4K > SD, then source order. Inside a category only that category is compared. Other copies become fallbacks: tried at once on error, after 7/9/12 s with no picture (profile), or after an 8/10/14 s mid-stream stall; copies that just failed are tried last for 10 minutes (memory only). Screen text: "Trying backup stream n of m".
