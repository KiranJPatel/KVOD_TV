# Google TV-inspired redesign — status

Approved design: top pill tab bar, dark and light themes, 1080p quality lock by default, remaining screens in the same visual language.

## Done in code (NOT yet compiled or run on a device)
- **1080p policy** (`QualityMode`, `Media3PlayerRepository.applyQuality`): max video height 1088 (tolerates 1920x1088), viewport constraint lifted, highest supported bitrate forced when locked. Modes: Best up to 1080p (locked, default), Auto up to 1080p, 720p, 480p. Setting + player "Quality" dialog + backup support. The offered variants and the policy's pick are written to the diagnostics log; the player chip shows the real decoded size, bitrate and frame rate.
- **Theme**: new neutral charcoal dark palette and a cleaner light palette; blue accent tuned per brightness; high-contrast focus ring (`focusRing` token).
- **Navigation**: `app/TopBar.kt` replaces the left rail (Home, Live TV, Movies, Series, Guide, Library + search, menu, clock, profile). Sources, Local media, Profiles and Settings are under the menu button.
- **Home**: old in-screen header removed (replaced by a source status line); Google TV-style hero (soft backdrop fading into the page, title/now line/actions on the left); channel cards are 16:9 tiles with name and now-playing underneath.
- **Live TV**: pill category list, borderless channel rows with logo tiles, larger details pane with the muted live preview; screen title removed (the top bar shows where you are).
- **Movies / Series**: no screen titles; 190x285 posters; detail screens use a large backdrop fading into the page (poster stays on the left when there is no backdrop); episode rows are borderless.
- **EPG / Settings / Library**: surface panels with larger radii, outline-only focus on rows, Favorites renamed Library. Screens without the top bar keep the overscan top margin (`LocalTopBarVisible`).
- **Sources / Profiles / Library**: Sources list and detail are rounded surface panels; profile tiles use rounded surface cards; Library gained a History tab (continue-watching items plus recently watched channels).
- **Add / Edit source**: two rounded panels. The left shows the source type, a short description and a vertical step list (Source, Test or Read, Preview, Import). The right holds the fields and actions. When editing, a Danger zone offers *Remove this source* with Cancel, Remove (keep favourites) and Remove everything. Sources screen: *Remove source* is now a red button.
- **Profile editor**: profile list panel plus an editor panel grouped into Avatar colour, Restrictions, Language, Security and a Danger zone (*Delete profile*, not shown for the owner). *Save name* sits next to the name field.
- **Forms**: text fields and the on-screen keyboard use the same rounded, high-contrast focus style.
- **Player**: quality is now a bottom-right side sheet (policy options, what is playing, what the stream offers).
- **Stall hint**: three rebuffers within 90 s while locked to 1080p show "Slow connection? Press OK, open Quality and choose Auto." (`StallDetector`, unit-tested). It never switches quality on its own.
- **Player (earlier)**: overlay gradients use the new neutral dark; Quality button and resolution chip added earlier.

## Layout grid and navigation audit
- **Fixed layout grid**: the UI is laid out for 1280 x 720 dp on every TV. The theme derives the density from the real display size (`referenceDensity`), so 720p, 1080p and 4K outputs, and Fire TV models that report different densities, all give the same layout and nothing falls off the screen. Sizes added during the redesign were converted from the 1920 px mockup to this grid (cards 190 dp, posters 168x250 dp, hero 290 dp, Live TV panes 240 / 400 dp).
- **Add / Edit source**: one screen, no scrolling for any source type (two fields per row, one action row: main action, Cancel, Remove at the far end). New URL / Xtream sources start with focus in the address field.
- **Profile editor**: one labelled row per topic (Avatar colour, Restrictions, Language, PIN, Delete), about 470 dp tall, so no scrolling.
- **Live TV list**: channel number, name, current programme and a thin progress bar per row.
- **Player overlay**: smaller control buttons (64 dp, Play 76 dp), less vertical padding so more video stays visible; while the overlay is visible, Up/Down change channel (when D-pad zapping is on).
- **Top bar**: side groups wrap their content so large text sizes do not push the tabs off screen.

## Typography and control sizing
See `docs/TYPOGRAPHY.md`: spec roles in every theme, one font setting, shared control-size tokens, Interface size setting.

## Next phases
light-theme visual pass on a real TV, then fixes from your build.

## Check on the TV
Fire TV: Settings > Display & Sounds > Display > Video Resolution must be Auto or 1080p. At 720p the device itself outputs 720p whatever the app requests.
