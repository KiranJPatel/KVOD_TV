# Feature checklist
Legend: **Implemented (untested)** = code written, never compiled/run here. Nothing in this project is "Verified". Tests exist but have **not been executed**.

## Implemented (untested)
- Foundation: TV manifest (touch optional, leanback launcher, banner), 4 themes + 5 accents + text scaling, design system (FocusSurface with outline/glow/1.04× scale, buttons, chips, cards, skeletons, state panels, dialogs, text field, on-screen keyboard, PIN pad), expanding left rail, overscan-safe frames, Back handling.
- Sources: SAF file import (copied to private storage, no storage permission), URL import with validation/redirect/size/timeouts/bounded retry, HTTP warning, consent checkbox, import wizard with preview/diagnostics/cancel/retry, Xtream wizard (account test, expiry/status shown only if returned), XMLTV URL/file, share-intent URL prefill, local video files (SAF), sample/demo data, rename / edit / refresh / enable / active source / reorder / merge duplicates / remove with optional favourites purge / diagnostics, WorkManager periodic refresh.
- Parsers: streaming M3U parser (quoted attributes, BOM, mixed EOLs, EXTGRP, safe EXTVLCOPT headers, relative URLs, scheme allow-list, entry limits, issue reporting), metadata-first classification (live/movie/episode/unknown), XMLTV parser (time zones, gzip, DOCTYPE disabled, lazy sequence).
- Catalog: stable ids (survive reorder/refresh), batched transactional imports, stale removal, duplicate skipping, Room schema v1 with indexes.
- Live TV: categories (+Favorites, Recently watched), filter, sort, source filter, now/next, favourite star, details panel, focus restoration after playback.
- Player: Media3 singleton, lifecycle release/restore, bounded exponential retry, error model, audio/subtitle selection, picture mode, overlay auto-hide, D-pad zapping, last channel, channel list, seek bar only when seekable, progress persistence, autoplay next episode, confirm-exit.
- EPG: matching (user mapping → tvg-id → optional exact unique name), grid with ruler, current-time line, details panel, day selector (7 days if data exists), favourites filter, search, jump to now.
- VOD: movies/series grids with category, search, sort; details; resume; episodes by season; lazy Xtream episode/plot loading; watched state.
- Favourites (live/movies/series), profiles (avatar colour, kids, hide-adult, blocked categories, audio/subtitle language, PIN with throttling), profile isolation of favourites/history/progress, inactivity lock, global search with recent searches.
- Settings: appearance, playback, EPG zone, parental lock, cache clearing, diagnostics (redacted), reset, about/licences.

- Redesign phases 1–5 + two-pane Settings (see `docs/DESIGN.md`): split-hero Home, round-icon player dock + zap banner, refined EPG, Live TV preview pane.

- Gap-closing round: manual **guide-channel mapping** (Live TV → Match guide channel; explicit mapping beats automatic matching), **per-source guide time offset** (Sources wizard), **JSON backup/restore** of settings, profile settings and source names (never secrets/PINs/favourites/history), **Media3 MediaSession** (hardware/remote media keys), **persistent redacted diagnostics/playback log**, low-storage guard with a clear message, Button semantics on every focusable.

- **Category types** (Sources → Category types): force a playlist group to Live / Movies / Series; applied on the next refresh (stored in table `group_overrides`, part of schema v1 because the app has not shipped yet — add real migrations from the first release on).

- **Paging 3** for Live TV, Movies and Series lists (page size 60, prefetch 30; SQL filtering incl. profile restrictions; channel queue ids fetched separately for up/down zapping).
- **System notification / transport controls**: `PlaybackService` (Media3 `MediaSessionService`, foreground type mediaPlayback) hosts the app session; started only for real playback, not for the muted Live TV preview; notification permission requested on API 33+.
- **Picture-in-picture**: pressing Home during playback enters PiP where the device exposes the feature (`FEATURE_PICTURE_IN_PICTURE`); UI chrome is hidden in PiP.
- **Voice search**: a *Voice* button in Search uses the platform speech recognizer when one is installed; `ACTION_SEARCH` queries from the system are routed to Search.
- **QR-assisted URL entry**: the TV shows a QR code for a one-time, token-protected page on the home network (LAN address only, 5 min, single use, URL validated); the phone types the address and it appears in the form.
- **External subtitle files** (.srt, .vtt, .ass/.ssa, .ttml) chosen with the file picker for movies/episodes, remembered per item, applied by restarting at the current position.
- **Metadata provider (TMDB, opt-in)**: user's own API key stored encrypted; fills only missing plot/poster/backdrop/year/rating by exact title match; throttled; cached in the catalogue; attribution shown.
- **Backup v2**: also favourites, Continue Watching positions and recently watched channels per profile, stored by title and re-attached after the matching source is imported (pending file retried after every import).

- **Widevine DRM (official Media3 path)**: a protected item plays when your own playlist supplies `#KODIPROP` `license_type=…widevine…` and an http(s) `license_key`; no key extraction, no bypass, other DRM types are reported and ignored. Device Widevine level decides the maximum quality.

- **App language**: Settings → Appearance → Language (System / English / हिन्दी). Visible UI strings go through `tr()`; translations are plain asset files (`assets/i18n/<code>.properties`, English text = translation) with English fallback, so new languages need no code change (template: `docs/i18n/catalog_en.properties`). Hindi covers ~240 strings.

## Partially implemented
- **Paging**: Live TV, Movies and Series are paged; Home, Search and EPG use small bounded queries. *Merge duplicates* uses a correlated SQL sub-query and can be slow on very large catalogues.
- **Season entity**: season is a column on episodes.
- **Focus restoration**: implemented for Live TV and Movies; other screens restart at their first control.
- **Search**: LIKE on normalised columns with indexes on name columns (not FTS); programme search is exact-id based.
- **Migrations**: schema v1 baseline only; migration tests will be needed with v2.
- **Performance targets**: not measured. No benchmark harness included.

## Unsupported / not implemented
- Translation of messages generated outside the UI layer (import/network error text, diagnostics) and of strings built with `${}` templates; voice search through Alexa on Fire TV (only the platform recognizer is used), camera-based QR scanning (TVs rarely have cameras; the phone-assisted page replaces it), offline downloads of provider content (intentionally not provided), ClearKey/PlayReady, multi-user cloud sync, subtitle files for live streams, remote subtitle URLs (refused on purpose).
