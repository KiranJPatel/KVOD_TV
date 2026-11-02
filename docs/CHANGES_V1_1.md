# KVOD TV 1.1.0 — what changed

Rename: the app is now **KVOD TV** (launcher label, TV banner, launcher icon, in-app logo and texts, Hindi strings). The logo is the **Spark** mark (a rounded play triangle with a spark on a light-orange tile; no lettering). Sources: `docs/logo/kvod_logo_spark.svg`, `docs/logo/kvod_icon_512.png` (512 px store icon). The Android *application id*
(`com.iptv.firetv`) and Kotlin package names are unchanged on purpose, so this installs over the previous build and keeps its data. The HTTP
User-Agent (`IPTV-FireTV/0.1`) is unchanged so providers that already accept it keep working; you can set a custom one per source.

## Player
- **Subtitles** (Settings > Subtitles, and *Subtitles > Subtitle style…* in the player): six one-tap **presets** (Classic, Cinema = new default, Soft Glass, Golden, Night Owl, Large Print) plus individual choices for text colour (7), background (black box / semi-transparent / **transparent**),
  size (small / medium / large / extra large), font (6), edge (auto / none / outline / shadow), position (bottom / raised / middle / top), live preview, reset.
  A transparent background automatically gets an outline so text stays readable. Embedded styles in the stream are ignored so your choice always wins.
- **Volume** and **Brightness** buttons. Volume is the app's own player volume (0–100 %, mute button); the TV volume is still your remote's. Brightness dims the
  picture with an overlay (20–100 %), because an app cannot change a TV's backlight. Both are saved.
- **Favourite** moved directly left of Play/Pause. **Random** toggle (shuffle icon): while on, channel Up/Down/CH± pick a random channel from the list you
  started from (never the current one, and not one of the last few). It is per viewing session: starting a channel from a list turns it off again.
- **Add to playlist** button (channels and movies). All dock labels now show on the focused button only, so 13 controls fit on one row.
- The TV is kept awake (no screensaver / sleep) while a stream is playing or loading (Settings > Playback > *Keep the TV awake*, on by default).

## Live TV and Library
- **Random** button before the filter box on Live TV; a **Play random channel** card at the start of the Library > Live TV grid and inside a playlist.
- **Playlists** (unlimited, per profile): Library > Playlists (create / open / rename / delete), playlist button on every Live TV row, *Add to playlist* in the player and
  on movie and series pages, Menu key on any Library card, and playlists appear as categories in Live TV. Included in backups.
- Pressing **Down from the top bar on Live TV goes to the category list**, not the search box (every screen can register its own entry point).
- **Settings > Menu & navigation**: hide the *Movies* and/or *Series* tab (Home rows for them hide too), and show/hide the *channel preview pane* (default on).
- **Back / navigation**: Back returns to the previously visited top-level screen (Live → Movies → Back → Live), each tab keeps its scroll position and filters;
  Back from the player returns to the list you came from, with focus on the channel.

## Look
- Three themes (Aurora, Space Gray, Studio) × dark/light with bundled fonts, see `THEMES.md` and `docs/design/`. Redesigned Settings (section sidebar, grouped cards, switches, theme gallery), a menu sheet
  instead of the stack of buttons, a Settings gear in the top bar, a new Add-source chooser (tiles) and form, redesigned Sources and Profiles screens, new dialogs.

## Data / build notes
- Database version **2** (new tables `playlists`, `playlist_items`) with migration `AppDatabase.MIGRATION_1_2`. On the first build Room writes
  `app/schemas/.../2.json`; **commit it** (the migration test needs it).
- New permission: `WAKE_LOCK` (network wake mode while playing).
- Version 1.1.0 (code 2). Not compiled in the authoring environment (no Android SDK): expect to fix a handful of compile errors on first build.
