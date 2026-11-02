# Multimedia compatibility matrix

Status words as defined in the specification. **No row is FULL**: FULL requires verified playback on the test device and no device test has been run. "Library" means Media3 1.5.1 documentation/source knowledge, not evidence. The authoritative per-device answer is the in-app report (Settings → Privacy & diagnostics → Playback capability report), which reads MediaCodecList, AudioManager, AudioTrack, Display and MediaDrm.

## Video codecs
| Codec | Status | Notes |
|---|---|---|
| H.264, HEVC, VP9, AV1, MPEG-2, MPEG-4 Part 2, VP8, MJPEG | HARDWARE or SOFTWARE depending on device (NOT TESTED) | Platform MediaCodec only; the report lists the actual decoder and limits |
| VC-1/WMV, RealVideo, Theora, DivX/Xvid in AVI | NOT IMPLEMENTED | Needs FFmpeg decoder extension; Media3 does not ship it on Maven |
| H.266/VVC | UNSUPPORTED | No Android platform decoder to target |
| Dolby Vision | CONDITIONAL | Only if the device decoder and TV report it (report shows HDR types); profile 5/8 dependent |
| HDR10, HLG | CONDITIONAL | SurfaceView path, display must report HDR; not verified |
| HDR10+ | CONDITIONAL / NOT TESTED | Needs device decoder + display support |
| 4K | CONDITIONAL | New `Best available` quality mode; 1080p-capped modes unchanged. 8K: NOT TESTED, report flags decoder 8K limit only |

## Containers
MP4/M4V/3GP/fMP4/CMAF, MKV/WebM, MPEG-TS, MPEG-PS, FLV, Ogg: supported by Media3 extractors (NOT TESTED). AVI/ASF/WMV/MXF/RealMedia/OGV-Theora: verify per Media3 release; WMV/ASF/Real NOT IMPLEMENTED. Container support never implies every codec inside plays.

## Audio
| Item | Status |
|---|---|
| AAC, MP3, Vorbis, Opus, FLAC, PCM/WAV, AMR | Platform decoders / Media3 (NOT TESTED) |
| AC-3, E-AC-3 | Passthrough when the HDMI sink reports it (Media3 auto-detects); no software decode → CONDITIONAL |
| E-AC-3 JOC (Atmos), TrueHD, DTS, DTS-HD, DTS:X | CONDITIONAL: passthrough only, depends on receiver; no licensed decoder bundled. Report lists `isDirectPlaybackSupported` on Android 10+ |
| ALAC, APE, DSD, WMA, Auro-3D, 360RA | NOT IMPLEMENTED |
| Bit-perfect / hi-res | Not claimed. Android mixer may resample unless passthrough/offload is used |
| Audio delay, normalization | NOT IMPLEMENTED |

## Subtitles
SRT, WebVTT, SSA/ASS (Media3 simplified rendering, not full styled ASS), TTML/DFXP: side-load + embedded (NOT TESTED). PGS: embedded in MKV/TS supported by Media3; `.sup` side-load mapping added, CONDITIONAL/NOT TESTED. DVB subtitles, CEA-608/708: Media3 (608 now exposed for HLS), NOT TESTED. VobSub, Teletext: NOT IMPLEMENTED. Subtitle delay: NOT IMPLEMENTED. Indic/RTL shaping: system text rendering, NOT TESTED.

## Streaming
See IPTV_PROTOCOL_SUPPORT.md.
