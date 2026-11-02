# Typography and control sizing

Implements the attached "Netflix-style typography" specification across the whole app, in every theme. The existing project is Jetpack Compose only (no XML layouts), so the roles live in the existing Compose theme (`core/ui/Theme.kt`); no second typography system exists.

## Font
`AppFontFamily` (Theme.kt) is the single place that selects the font. Netflix Sans is proprietary and is not bundled; the system sans-serif (Roboto on stock Android) is used. To use Roboto or a licensed font from `res/font`, change that one value.

## Roles (`TypeRoles`) and Material mapping
| Role | Spec (sp / weight / line / tracking) | Used for (Material role) |
|---|---|---|
| Hero | 32 / 700 / 38 / -0.4 | movie titles, welcome and profile headings (displayLarge, displayMedium) |
| Screen title | 26 / 700 / 32 / -0.3 | screen titles, hero banner title (displaySmall, headlineLarge, headlineMedium) |
| Section heading | 18 / 700 / 23 / 0 | row headings, panel titles, dialog titles (headlineSmall, titleLarge) |
| Content title | 15 / 600 / 19 / 0 | channel, poster and episode titles, now-playing (titleMedium, titleSmall) |
| Body | 14 / 400 / 20 / 0 | descriptions, list text, form fields (bodyLarge, bodyMedium) |
| Metadata | 12 / 400 / 16 / 0 | supporting text (bodySmall) |
| Nav label | 12 / 500 / 16 / 0 | top tabs, chips, round-button labels (labelMedium) |
| Caption | 10 / 400 / 13 / 0 | small badges (labelSmall; LIVE is bold) |
| Button | 15 / 700 / 19 / 0 | all buttons (labelLarge) |

## Deviations from the specification
1. **TV scale factor 1.333.** The spec's sp values are for a 960 dp wide screen. The app lays out on a 1280 dp grid (see below), so every size, line height and tracking is multiplied by 1280 / 960 to keep the same proportions of the screen. Resulting sizes: hero 43, screen title 35, section 24, content title 20, body 19, metadata 16, button 20.
2. **Nav label** uses the 12 sp metadata size with Medium weight instead of 11 sp: tabs and chips are read from about 3 m.
3. **Button label** uses 15 sp (middle of the 14-16 range).
4. Letter spacing is in sp (Compose `TextUnit`), not px.
5. Every theme (Midnight, Material dark, Material gray, Light) uses the same roles; the old per-theme weight and line-height tweaks were removed.

## Layout grid and control sizes (`Dimens`)
The theme derives the density from the real display size so the layout is 1280 x 720 dp on any TV and output resolution. Settings > Interface size (85 / 100 / 115 %) scales the whole interface for a particular TV (Text size still scales text only).

| Control | Size | Notes |
|---|---|---|
| Button | 44 dp high, 20 sp bold label, 20 dp inline icon | icon is about the label size |
| Chip | 38 dp high | |
| Top tab / circle button | 40 dp, icons 22 dp | |
| Round player control | 52 dp (Play 64 dp), icons 26 / 32 dp | label 16 sp |
| List option row / text field / key | 48 / 48 / 44 dp | |
| Settings and consent rows | 56 dp | |
| Favourite star | 44 dp, 24 dp icon | |

## Uniformity sweep
- Cards and rows: channel, poster and landscape cards, Live TV rows and EPG cells all use *Content title* (semi-bold) for the title and *Metadata* for the subtitle. Landscape cards are 16:9 (260 dp wide), channel cards 190 dp, posters 168 x 250 dp.
- Check boxes: one `CheckRow` component (56 dp row, 24 dp check icon, Body text) replaces the text-character check mark in the import consent.
- Dialogs: 520 dp wide, 24 dp corners, 24 dp padding, Section-heading title.
- Avatars: the initial uses the Hero size only on large avatars; small ones use the Section-heading size. Lock badge 28 dp.
- EPG: one toolbar row (days scroll on the left; Now, Favorites, Search and Live TV on the right; search opens a dialog), details panel 88 dp, ruler 28 dp, rows 64 dp, legend removed (the time zone sits in the ruler's channel column). About 130 dp more of the screen shows guide rows than before and no row is taller than a chip.

## Verification status
Built on paper only: no Android SDK was available while editing, so nothing here is compiled or run. `TypographyTest` and `LayoutGridTest` check the role table, theme consistency and the density maths. Visual checks on the Toshiba TV (and at Text size Large / Extra large) are still required.
