# Existing player audit (KVOD TV V3, Media3 1.5.1)

Method: every file under `core/player`, `feature/player`, `feature/live`, `data`, `core/network`, `core/parser`, Gradle files and the manifest was read. **Nothing here was run:** the audit environment had no Android SDK, Gradle or device (see KNOWN_LIMITATIONS.md). Statements marked *(code)* come from reading source; nothing is marked verified.

## Engine
Single `ExoPlayer` (AndroidX Media3 1.5.1: exoplayer, hls, dash, rtsp, session, ui, okhttp datasource), one `MediaSession`, `PlaybackService` for notification/transport. No FFmpeg, no RTMP module, no DRM beyond Media3 Widevine (licence URL from the user's own `#KODIPROP`). UI is `PlayerView` (SurfaceView by default, so HDR/Dolby Vision output is not blocked by the view type).

## Findings (ID, evidence, effect, status)
| ID | Finding | Effect | Status |
|---|---|---|---|
| F1 | Media data source used the playlist client, which has `followRedirects(false)` | Any stream URL answering 301/302 failed as "server refused the stream"; many IPTV providers redirect to an edge server | Fixed: dedicated `MediaHttp` client, redirects on, https→http blocked |
| F2 | Data source factory was OkHttp-only | `udp://`, `content://`, `file://` could never open in the engine; parser also rejected `udp` | Fixed: `DefaultDataSource` wraps OkHttp; `udp` allowed in M3U |
| F3 | `setUserAgent` was sticky across channels and also added a second User-Agent header when the playlist supplied one | Wrong/duplicate UA on later channels | Fixed: headers built per data source |
| F4 | `PlayerViewModel.load` ran favourites, "all channel ids" (up to 20 000) and up to 500 sequential `catalog.channel()` calls (each re-reading profile restrictions) **before** `repo.play` | Large part of the "buffering takes long" complaint on big playlists | Fixed: play first, extras after, one batched query |
| F5 | Live preview was released and the channel re-tuned from scratch on fullscreen | Second connection + new decoder at every OK press | Fixed: `promotePreview` keeps the running stream |
| F6 | No decoder fallback | Decoder init failure = playback failure | Fixed: `setEnableDecoderFallback(true)` |
| F7 | MPEG-TS extractor flags at defaults | Start waits for a full IDR frame | Fixed: non-IDR keyframes + access-unit detection (HLS and progressive TS) |
| F8 | HLS CEA-608 not exposed when playlist omits CLOSED-CAPTIONS | Captions missing on some IPTV HLS | Fixed (`exposeCea608WhenMissingDeclarations`) |
| F9 | Duplicate merge was a hidden-by-default switch, only across all sources, name/tvg key only, no fallback use | Duplicates listed; backups never used | Fixed: always hidden, quality-ranked, used as fallbacks |
| F10 | Retry = same URL after 1/2/4 s | A dead copy was retried while a good copy existed | Fixed: copies tried first, then backoff |
| F11 | All quality modes cap at 1080p | 4K streams could never be selected, even on a 4K stick | Added `BEST` mode (device-limited, adaptive) |
| F12 | No audio/subtitle delay, no catch-up, no RTMP/SRT, no FFmpeg | Gaps, see matrix | Documented, not implemented |
| F13 | Tunneled playback off, frame-rate matching at Media3 default | No change made | Documented |

## Not changed on purpose
Media3 stays at 1.5.1: a version bump cannot be build-verified here and the project is otherwise pinned. Check for the current stable release before shipping. Cleartext HTTP stays enabled (providers are HTTP-only; the app already warns). Existing UI, navigation, themes untouched except: Sources screen merge switch removed, one Settings row added (capability report), one loading-text variant ("Trying backup stream n of m").
