# Midnight Cinema — design notes (approved)
Approved choices: split hero on Home; small **muted live video preview** in the Live TV details pane; **no** poster rank numerals; subtle motion (180–200 ms); system font.

## Tokens
Palette per theme in `core/ui/Theme.kt` (Midnight = spec palette). Extra tokens: `focusGlow`, `live`, `success`, `warning`, `divider`, `scrim`, **`nowTint`** (accent at 18 % for "on now" items).
Type scale (min 16 sp): display 56/46, headline 40/32/28, title 26/22/20, body 20/18/16. Text size 90–130 %.
Layout: 1920×1080 reference, overscan margins 48/27 dp, rail 84 dp collapsed → 260 dp expanded (overlays content, no layout shift).

## Focus rule (everywhere)
`FocusSurface`: 3 dp glow-colour ring + 14 dp soft glow + 1.04× scale (graphicsLayer, no layout shift). Selected-but-unfocused: filled accent pill (rail, chips).

## Implemented in code (phases 1–5, unverified build)
1. **Foundation**: `nowTint` token, rail selected pill + divider before Sources + red live dot when a favourite channel is on air, `LandscapeCard`, `RoundIconButton`, `Chip(dim)`.
2. **Home**: top bar (source status dot, search, profile avatar, clock) and the split hero — left: LIVE chip/tag, title, description, *Watch now* + *Add to favorites*; right: 16:9 preview card with channel, now/next line and progress. Hero crossfades (200 ms) as focus moves. Rows: Live, Continue Watching (16:9 resume cards), Favorites, Recently watched, Movies, Series, Recently added.
3. **Player**: top info strip (logo, title, now programme, LIVE, resolution chip, large channel number); bottom dock (NOW/NEXT with progress, audio/subtitle status, "not seekable" note, round icon controls with labels, large Play); two-second channel banner when zapping with the overlay hidden (Up/Down no longer opens the overlay).
4. **EPG**: day tabs dimmed when the feed has no data for that day (from the real last-programme time), "on now" tint, current-time line with time tag, details strip with *Watch channel*, legend and time-zone label.
5. **Live TV**: 380 dp details pane with a 16:9 muted live preview of the focused channel (starts after ~0.9 s of focus, falls back to the logo while loading/on error, Settings → Playback → "Live video preview" to disable). The preview shares the single ExoPlayer; it is released when leaving the screen and never interferes with a real playback.

## Phase 6
Done in code: two-pane Settings with a theme preview row; movie/series details with meta pills and a faded backdrop; Sources wizard with a step indicator (Source > Test/Read > Preview > Import) and an amber HTTP warning banner; a pure `RetryController` (extracted from the player) with unit tests for the retry transitions; tests for favourites persistence, profile isolation, watch progress, parental filtering (lists, search, direct lookup, Continue Watching), PIN switching, schema v1 migration scaffold, empty/error panel, channel queue and the sample guide.
Not done: the compile-fix pass (needs a real build), real-device checks of focus, playback and the Live TV preview, and performance measurements.
