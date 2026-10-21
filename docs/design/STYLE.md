# Voxara visual style

The look of both apps (Wear OS `:app`, phone `:phone`), taken from
[`voxara_template.html`](voxara_template.html). Only the **style** comes from the template:
colours, type, shapes and the shape of its visuals. Its features (VoxBuds Pro, the headphone
"shield"/limiter, −15 dB buttons, L/R balance, earbud battery, anti-fatigue EQ, rest timer, its dose
math, its 80/88 dB thresholds and its "OMS" texts) are **not** part of Voxara. Every number, zone,
threshold and sentence still comes from Voxara's own logic (`NoiseDoseEngine`, `RiskZone`,
`AlertEngine`, the ledgers) and string resources.

Where the values live:

| What | Where |
|---|---|
| Raw colours, accent variants, contrast maths (pure, unit-tested) | `shared/…/core/design/Palette.kt` |
| Fonts, level-accent mapping, wave/capsule drawing shared by both apps | `shared-ui/…/ui/design/` (`SharedDesign.kt`, `Visuals.kt`) |
| Watch tokens and components | `app/…/ui/design/` (`Tokens.kt`, `Components.kt`, `Layout.kt`, `Numerals.kt`, `WatchVisuals.kt`), `app/…/ui/theme/VoxaraTheme.kt`, the glance pages in `app/…/ui/screens/` (`Glance.kt`, `HomeScreen.kt`) |
| Phone theme and components | `phone/…/ui/theme/PhoneTheme.kt`, `phone/…/ui/components/` (`Components.kt`, `Template.kt`, `Charts.kt`) |

---

## 1. Colour

### Neutrals

| Token | Dark | Light | Compose name |
|---|---|---|---|
| Background | `#090B10` | `#F3F5F9` | `background` |
| Card | `#131722` | `#FFFFFF` | `surface` / `VColor.Card` |
| Card, subtle (insets, tracks, inactive capsules) | `#1B2030` | `#EBF0F7` | `surface2` / `VColor.CardSubtle` |
| Border (1 dp) | white 8 % | `#0F172A` 7 % | `border` |
| Text | `#F8FAFC` | `#0F172A` | `text` |
| Text, secondary | `#AEB6C4` | `#334155` | `text2` |
| Text, muted | `#8A94A6` | **`#5B677A`** ¹ | `text3` |
| Capsule / track inactive | white 12 % | `#0F172A` 10 % | `track` |

¹ The template's light muted `#64748B` is only 4.36:1 on `#F3F5F9` and 4.16:1 on `#EBF0F7`, below
WCAG AA. It is darkened to `#5B677A` (≥ 5.0:1 on every light surface). The thresholds were not touched.

**Watch:** pure black `#000000` background (AMOLED; the watch is dark only). Cards use the dark
card `#131722`, so they read as lifted panels on black.

### Fixed hues

| Role | Value | Use |
|---|---|---|
| Secondary | `#6366F1` | Second series in charts (headphones vs surroundings), Tono's brand mark |
| Warning (caution tier) | `#FFB800` | Level-reactive accent when the zone is LOUD |
| Danger (danger tier) | `#FF2A6D` | Level-reactive accent when the zone is DANGEROUS |

### Accent palette (user choice on the phone; the watch is always Purple)

The four template swatches. Each has a **fill** (rings, capsules, bars, switch track, primary
button), an **ink** (text and icons in accent colour), an **on-fill** text colour, a **soft**
(fill at 15 % alpha: badge and icon-tile backgrounds, nav pill) and a **glow** (fill at 35 % alpha:
switch glow, number glow, halo). Fill and ink differ per theme so every pair passes WCAG AA.

| Accent | Dark fill | Dark ink | Light fill | Light ink | On fill |
|---|---|---|---|---|---|
| **Purple (default)** | `#8B5CF6` | `#A78BFA` | `#8B5CF6` | `#6D28D9` | `#05080D` |
| Turquoise | `#00E5BF` | `#00E5BF` | `#009E87` | `#00695C` | `#05080D` |
| Blue | `#3B82F6` | `#60A5FA` | `#3B82F6` | `#1D4ED8` | `#05080D` |
| Emerald | `#10B981` | `#34D399` | `#059669` | `#065F46` | `#05080D` |
| Caution tier | `#FFB800` | `#FFB800` | `#B7791F` | `#92400E` | `#05080D` |
| Danger tier | `#FF2A6D` | `#FF6B93` | `#FF2A6D` | `#9F1239` | `#05080D` |

Rules, enforced by `shared/src/test/…/core/design/AccentPaletteTest.kt` for **every accent × theme**:
- ink ≥ 4.5:1 on background, card, card-subtle, and on the soft tint over each of them;
- fill ≥ 3:1 on background and card (WCAG 1.4.11, non-text);
- on-fill ≥ 4.5:1 on fill;
- watch: Purple ink ≥ 4.5:1 and fill ≥ 3:1 on black and on the dark card.

Turquoise and Emerald are too light to be a graphic on white, so their light fills are deeper
shades of the same hue. Purple text on a white card uses `#6D28D9`, not `#8B5CF6`.

### Level-reactive accent

The accent follows the **current `RiskZone`** (Voxara's boundaries: OK < 70, Moderate 70–84,
Loud 85–94, Dangerous ≥ 95 dBA; for percentages 50 / 80 / 100 %):

| Zone | Tier | Colour |
|---|---|---|
| OK, MODERATE | safe | the chosen accent |
| LOUD | caution | amber |
| DANGEROUS | danger | crimson |

MODERATE stays on the accent because nothing alerts there; Voxara's level alert starts at 85 dBA
(default) and the 80 % dose warning sits in LOUD. The zone is still always spelled out with its word
and its icon (`ZoneTag`), so the tier colour never carries the zone alone. Colour changes between
tiers are animated (`animateColorAsState`, 400 ms; instant with reduced motion).

Where the reactive accent is used: hero cards, bezel ring, waves, halo, capsules, data bars, rings
and zone badges, i.e. whatever shows a level or a dose. App chrome (navigation pill, switches,
primary buttons, links, icon tiles, editorial labels) keeps the **chosen** accent, so the user's
choice stays visible and a red switch never reads as an error.

On the phone the reactive accent of the header and hero comes from today's dose zone (the phone
never shows a live level). On the watch it comes from the live level (Now, Home hero, ambient) or
from the dose/budget each ring shows.

---

## 2. Typography

| Family | Use | Weights |
|---|---|---|
| **Plus Jakarta Sans** (variable, OFL 1.1) | All UI text | 400, 500, 600, 700, 800 |
| **JetBrains Mono** (variable, OFL 1.1) | Phone numerals only (with `tnum`). **Not used on the watch**: its zero carries a dot ("30" reads "30·") that no OpenType setting removes (`zero`, `ss01`, `ss02`, `cv01`, `cv12` were tried) | 500, 600, 700, 800 |

On the watch every figure is Plus Jakarta Sans (plain zero, plain one), drawn one fixed-width cell per digit
(`TabularNumber` / `RollingNumber`, cell = widest digit of the style), so numbers stay tabular and can
roll. No watch style asks for the `tnum` feature: Plus Jakarta Sans' tabular alternates give "1" a slab foot
(it read as a different, serif font on "11" and "13"), and the cells already do the job. The unit is always written `dBA`. Both fonts are bundled in `shared-ui/src/main/res/font/` (`plus_jakarta_sans.ttf`,
`jetbrains_mono.ttf`, from github.com/google/fonts) with their licences in
`shared-ui/src/main/assets/licenses/`. Inter was removed (nothing else used it).

Scale (sp, so the system font size scales it; nothing below 12 sp):

| Style | Watch (`VType`) | Phone (`VoxTypography` / `VoxNum`) | Font / weight / tracking |
|---|---|---|---|
| Hero number | `Hero` 52 | `displayLarge` 56, `displayMedium` 44 (Home score) | Watch: Jakarta 800, −0.03 em · Phone: Mono 700, −0.04 / −0.03 em |
| Display number | `Display` 36 | `displaySmall` 32 (stat cards) | Watch: Jakarta 800, −0.02 em · Phone: Mono 700, −0.03 em |
| Numeral | `Numeral` 22, `Value` 13 | `VoxNum.large` 24, `.medium` 17, `.small` 12 | Watch: Jakarta 800 / 700 · Phone: Mono 600–700 |
| Editorial title | `Title` 19 | `headlineMedium` 26, `titleLarge` 21 | Jakarta 800, −0.02 em |
| Title small | `TitleSmall` 15 | `titleMedium` 17, `titleSmall` 15 | Jakarta 700 |
| Body | `Body` 14 | `bodyLarge` 16 / `bodyMedium` 15 / `bodySmall` 13 | Jakarta 500 / 400 |
| Eyebrow (uppercase) | — (removed on the watch) | `EyebrowStyle` 12 | Jakarta 700–800, +0.12 em |
| Caption / label | `Caption` 12, `Label` 13 | `labelSmall` 12, `labelMedium` 13, `labelLarge` 15 | Jakarta 500–700 |

## 3. Shape, depth, spacing

- Cards: 22 dp radius, 1 dp border (`border` token), soft shadow on the phone (elevation 2 dp,
  ambient black 8 %). The watch draws no shadows (black background, cost).
- Hero card: 26 dp radius, a deeper shadow on the phone (6 dp).
- Pills, badges, chips, switches, nav indicator: fully rounded.
- Icon tile: 36 dp rounded square (12 dp radius) in accent-soft with an accent-ink icon (phone; the watch
  uses a plain tinted icon in its rows).
- Spacing: 4-pt grid; phone gutter 16 dp, gap between cards 12 dp; card padding 16 dp
  (hero 18 dp).
- Touch targets ≥ 48 dp everywhere.

---

## 4. Components

| Component | Watch (`app/…/ui/design/`) | Phone (`phone/…/ui/components/`) | Notes |
|---|---|---|---|
| **Hero metric card** | `PageStage` + `OrbitStage` (`Glance.kt`, `WatchVisuals.kt`) with one picture per page from `Orbit.kt` (`NowRing`, `DoseMorph`) | — | Waves + scrim, the page's picture on the ring, figure, zone tag, foot (told its room) and one control above the dots | `HeroCard` (Template.kt) + Home's `HomeHero` | 26 dp card, uppercase subtitle, big Mono number, status badge pill (`ZoneTag`: tier-soft fill, tier ink, tier border), wave lines behind |
| **Wave lines** (horizontal) | — | `WaveLines` (shared-ui Visuals.kt) | 3 sine lines with an edge envelope; amplitude from today's dose; alphas 27 % / 12.5 % |
| **Segmented capsule meter** | 24 hourly capsules on Dose / Today (`HourBars`) | `CapsuleMeter` (Home hero) | 26 capsules, each in the tier colour of the zone it stands for, last lit one 16 % taller, unlit 14 % shorter, Mono scale marks |
| **Metric triplet** | — | `MetricTriplet` (Today) | Three small cards: uppercase label, Mono value, caption; stacked at font scale ≥ 1.5 |
| **Switch** | Wear M3 buttons/toggles in accent | `VoxSwitch` | 48×28 track, white thumb off, dark thumb on, accent track with a 3 dp glow ring |
| **Row with icon tile** | `MetricCard` (tinted icon, no tile) | `GroupRow` / `SwitchRow` + `IconTile` | Tile = rounded square in the tint's soft alpha, icon in the tint |
| **Screen title / group heading** | `ScreenTitle`, `GroupLabel` | `VoxScreen` (`eyebrow`) + `EditorialLabel` | Watch: one extra-bold title, sentence-case group headings in long lists, both headings; no uppercase label above anything |
| **Bottom nav / rail** | — | `PhoneApp` item colours | Muted items; selected: accent-soft pill, accent icon |
| **Bezel arc** | `BezelArc`, `LevelBezel` (ambient) | — | 250° (110° gap at the bottom), rounded caps, 2.7 % of the display, outer edge 4 % in from the glass on every page (ambient 11 %, clear of the clock), track white 7 %, glow = a 3× wider stroke at 18 % alpha (no blur) |
| **Headphones ring** | `NowRing` (`Orbit.kt`) | — | The dBA scale opens at the top (where the Moderate zone begins) into two ear-cup arcs (42° gap) that rise with the listening level, indigo (the tier colour when loud); the outside level is `Moon` in the gap |
| **Tono's tag** | `TonoTag`, `FittedStatus` (`Glance.kt`) | — | Icon (sparkle = cloud AI) + Tono's line in Label size on a black 55 % scrim, hairline border; 2 lines, 1 line or the first detail row by the room left; up to 76 % of the display wide; tap = Ask |
| **Today \| Week switch** | `DoseSwitch` (`DoseScreen.kt`) | — | Two tab segments in one 48 dp pill, the selected one filled with the accent tint and extra-bold; also flipped by the crown |
| **Headphones chip / card** | `HeadphonesChip`, `HeadphonesModeCard` (`HeadphonesLayer.kt`) | `Settings` → Headphones mode | 48 dp indigo pill "Headphones / Auto or Manual"; card = quick switch in Now's first detail |
| **Harmonic rings + halo** | `WaveHalo` (shared `HarmonicRings`) | — | 3 sine-deformed closed rings (4 finer while listening on headphones) + radial halo |
| **Level number** | `LevelNumber` + `RollingNumber` / `TabularNumber` | — | Per-digit-cell figure over a translucent radial glow (the template's text-shadow without blur); digits roll |
| **Glance pager** | `HomeScreen` (`HorizontalPagerScaffold`, `AnimatedPage`) | — | Now, Dose (Today / Week), More (Ask, Mode, Settings); the Wear page indicator hugs the bezel, with a black scrim where its dots sit |
| **Glance page / list** | `HeroList`, `VoxList`, `row` | — | First item one screen tall (hero), details below; items scale and fade at the screen edges |
| **Resume pill** | `ResumePill` | — | Replaces the zone badge while paused; 48 dp |
| **Page dots** | `PageDots` | — | Onboarding progress (the "Step n of m" text stays for TalkBack) |
| **Circular overlay** | `CircularOverlay` | — | Round dark panel with a 1.5 dp tier border: the limit alert; the border pulses once per haptic beat |
| **Accent swatches** | — | `AccentSwatches` | 4 round swatches (48 dp targets, radio semantics, colour name as description); selected one scaled 1.22 with a ring |

### Data mapping (no invented data)

| Template visual | Voxara data |
|---|---|
| Phone hero waves | Amplitude from today's dose (`head.dosePercent`) |
| Phone 26 capsules (30–110 dB scale) | Today's dose 0–120 % on Home (marks 0 / 50 / 80 / 100 %, Voxara's percent zones) |
| Phone status badge | Today's dose zone (word + icon) |
| Watch page pictures | Now: live level on a 30–110 dBA scale of four zones (headphones mode: the estimate on two arcs, or the week's listening share when nothing plays). Dose / Today: the 24 hourly levels (`DaySummary.hourly`). Dose / Week: the seven days' shares of the weekly allowance, each in its own dose zone. Ambient: the live level on a hairline arc |
| Watch harmonic rings | Amplitude and tier from the live level (Now, Home) |
| Scene / level band | First detail rows on Now (it has no pill on the hero) |
| Hourly bars | 24 hourly ledger levels (`DaySummary.hourly`), tier colour per hour |
| Template "max exposure" line | Voxara's own headroom text (`formatHeadroom`) |

---

## 5. Motion and battery (watch)

Motion explains a state change, a place or feedback; each item below has a reason, a duration and a
static fallback (reduced motion or ambient).

| Motion | Why | Duration |
|---|---|---|
| Arc, scale, bars or segments draw in when a page opens (later changes follow with a spring) | A page just opened: where the value is | 650 ms, decelerating |
| Zone colour crossfade (arc, rings, halo, badge) | The level crossed a tier | 400 ms |
| Level number rolls digit by digit (up when it rises, down when it falls) | The level changed | 220 ms |
| Wave rings follow the level with a spring; fill the ring's inside; drift slowly when quiet, faster and wider when loud | Ambience that reports the sound | spring; speed 0.9 to 5.2 rad/s |
| Headphones mode in: ring splits into two arcs (spring, accent to indigo), waves go stereo, the centre swaps and digits roll from the outside to the listening level, the outside level flies out as the moon, the curved label settles into the chip | The listening layer turned on: the signature moment | about 900 ms, staged ring, waves, figure, moon; out about 700 ms |
| Pages slide with the Wear pager animation; list items scale/fade at the screen edges | Place and continuity | Wear specs |
| Mode chip: label rolls to the new mode with a short pop, fill turns accent in Concert | Auto detection changed the mode | 220 ms |
| Page picture draws itself in when its page becomes current, retracts when it leaves, and turns up to 40° with the swipe | A page changed: a new picture, not only a new position | 650 ms in / 250 ms out |
| Today to Week (Dose): the 24 bars retract into the rim in turn while the day segments fill in; the figure rolls; caption cross-fades; the switch fill moves. Reduced motion: the pictures cross-fade | The view changed: the same ring shows the other period | 520 ms (200 ms cross-fade) |
| Zone tag swaps icon and word with a short pop | The zone changed | 150 ms |
| Taps shrink to 96 % while held | Feedback | 100 ms |
| Limit alert: border and icon pulse once per haptic beat (daily: 0/150/300/470 ms; weekly: 0/220/400 ms) | The haptic is firing: see it too | 420 ms (big) / 210 ms (small) per beat |

- Free-running animation (rings, pulsing dot) runs **only** when the screen is interactive and the
  activity is resumed: `LocalAnimate` (not ambient, "remove animations" off) gates it, and
  `animationsActive()` adds the lifecycle (RESUMED). Ambient, paused, or reduced motion → a static
  frame. Arc sweep, rolling digits and the chip morph are one-shot and also stop with reduced motion.
- The page pictures are event-driven (draw-in, data changes) and draw with cached `Stroke`s and precomputed colour arrays, so no frame allocates; only the wave rings run a free clock.
- One `withFrameNanos` clock per visual (`rememberWavePhase`), read only in the draw phase, so a
  frame redraws the Canvas without recomposing; each visual reuses one `Path` (`rewind()`) and its
  `Stroke`s, and rebuilds its halo brush only when the colour changes: no per-frame allocations;
  96 segments per ring. The pager keeps only the visible page(s) composed, so one clock runs at rest.
- No blur anywhere on the watch (minSdk 30): glows are translucent wide strokes / radial gradients.
- **Ambient:** no animation, no glow, no fills, outline-only bezel and number.
- Tiles and complications cannot animate: they get the colours, sentence-case text and 270° ProtoLayout arcs only.

## 5b. Colour roles added in pass 2

- **Indigo** (`VColor.Secondary`, ink #818CF8 ≥ 4.5:1 on black and the cards) marks headphones: page name, scale, confidence band.
- **Neutral note** (secondary text + info icon): uncalibrated estimate, other informative notes. Amber and crimson are only for zone warnings (and amber for a paused monitor).
- **Zone tag** over the waves: tier tint over a black 62 % scrim; `DesignSystemTest` checks the text against the brightest ring colour.

## 5c. Pass 3: filling the screen, and headphones mode

- **Scaling.** Nothing in a page's geometry is a fixed dp. `Face` (`app/…/ui/design/Face.kt`) holds the fractions: ring outer edge 4 % in, stroke 2.7 %, wave box 3.5 % in, centre column from 15 % down and at most 72 % wide, foot at most 66 % wide and 9 % clear of the bottom. Figures scale 0.9 to 1.15 times with the display (216 dp = 1.0), words 1.0 to 1.12 times (never below the 12 sp floor). Large fonts: figure and Tono's line held at 1.3 times, other hero words at 1.5 times.
- **One rim.** Every picture sets its stroke's outer edge on the rim (`ringRadius(w) = rim - w/2`); Today's bars hang inward from it. Measured on the real watch (432 px): the ring's outer edge was 57 to 78 px from the glass across the five pages before, 18 to 22 px after (the Now page's glow and the Week's dotted no-data days read nearer or further by a few px).
- **Colour roles.** Indigo fill (`VColor.SecondaryFill` #6366F1, ≥ 3:1 on black and cards) for the split arcs, chip border and tint; indigo ink for the chip text. The moon keeps the outside zone's colour on a black disc.
- **Week ring.** Seven segments with clear gaps, a day with data always shows at least a stub, a day with none is a dotted line, today is wider and underlined in white; every day is also spoken ("Monday: 3 %. … Friday (today): 12 %").
- **Headphones mode** never changes a threshold, measuring, a ledger write or an alert (`ListeningLayerPolicy`, `ListeningLayerSafetyTest`).

## 5d. Pass 4: three pages

- **Structure.** Six pages became three: **Now** (the old Overview and Now: the dBA scale, the figure, the zone tag, the uncalibrated note, Tono's one line as a tag, today's dose as the foot; headphones mode transforms it), **Dose** (the old Today and Week on one ring that morphs; Today | Week switch or crown) and **More**. The Headphones page is gone (estimate: Now in headphones mode; weekly share: Dose / Week; settings: Mode).
- **Names.** The visible page names are gone (the figure and unit, the switch and the title say what a page is); TalkBack still hears "Page n of 3, name".
- **The foot has a budget.** The gap under the centre is about 49 dp tall on a 203 dp display. `PageStage` gives the foot its room; Now shows Tono's tag in 2 lines, 1 line or not at all (it is then the first detail row), and a short dose line ("46% of today" / "Not adding · 46% today") because the last line in a circle's bottom gap can only be about 115 dp wide at 203 dp. The full sentence (headroom, "not adding to dose") is the first detail.
- **The crown** flips Today and Week while the Dose page rests at its top (`HeroList(onRotary)`); scrolled, or turning on past Week, it scrolls the details as usual.
- **Bottom scrim.** Black where the page dots sit, so a list's last visible item never shows through them.

## 6. Accessibility

- Touch targets ≥ 48 dp; text ≥ 12 sp (the "%" beside a figure included); layouts checked at font
  scale 1.0 / 1.3 / 2.0 on the watch (192, 216, 240 dp) and 1.0 / 1.3 / 2.0 on the phone. At large
  sizes the hero caps its figure at 1.3 times and shows its foot as the first detail.
- Headphones mode turning on or off is announced ("Headphones mode on, outside sound still measured"); Now's description says it is on, the estimated level, the outside level and its zone.
- Every glance page is spoken as one description prefixed with its place ("Page 2 of 3, Dose"); the More title carries the same prefix. Dose speaks the view it shows (today's share and loud hours, or the week's two shares and each day) and its switch is a pair of tabs ("Today, tab, selected").
- Decimals, dates and times use the language the app shows (the configuration locale), not the JVM default.
- Headings carry `heading()` semantics (editorial titles, section labels).
- Zones always as word + icon; charts keep their spoken descriptions.
- Every accent × theme passes the contrast tests (section 1).

## 7. Phone appearance settings

Settings → **Appearance**: accent (Purple · Turquoise · Blue · Emerald, default Purple) and theme
(Light · Dark · Follow system, default **Light**). Stored in `PhoneStore` (DataStore keys
`accent`, `theme_mode`), phone-only: not a synced `SettingField`, never sent to the watch. Applied
live through `VoxaraPhoneTheme` (CompositionLocals), the system bars, the window background and,
on Android 12+, `UiModeManager.setApplicationNightMode` so the splash follows; the Glance widget
reads the same keys.
