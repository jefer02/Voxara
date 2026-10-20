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
| Watch tokens and components | `app/…/ui/design/` (`Tokens.kt`, `Components.kt`, `Layout.kt`, `WatchVisuals.kt`), `app/…/ui/theme/VoxaraTheme.kt` |
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
and its icon (`ZoneBadge`), so the tier colour never carries the zone alone. Colour changes between
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
| **JetBrains Mono** (variable, OFL 1.1) | Every dB, %, time and counter, with `tnum` | 500, 600, 700, 800 |

Both are bundled in `shared-ui/src/main/res/font/` (`plus_jakarta_sans.ttf`,
`jetbrains_mono.ttf`, from github.com/google/fonts) with their licences in
`shared-ui/src/main/assets/licenses/`. Inter was removed (nothing else used it).

Scale (sp, so the system font size scales it; nothing below 12 sp):

| Style | Watch (`VType`) | Phone (`VoxTypography` / `VoxNum`) | Font / weight / tracking |
|---|---|---|---|
| Hero number | `Hero` 52 | `displayLarge` 56, `displayMedium` 44 (Home score) | Mono 700, −0.04 / −0.03 em |
| Display number | `Display` 36 | `displaySmall` 32 (stat cards) | Mono 700, −0.03 em |
| Numeral | `Numeral` 22, `Value` 13 | `VoxNum.large` 24, `.medium` 17, `.small` 12 | Mono 600–700 |
| Editorial title | `Title` 19 | `headlineMedium` 26, `titleLarge` 21 | Jakarta 800, −0.02 em |
| Title small | `TitleSmall` 15 | `titleMedium` 17, `titleSmall` 15 | Jakarta 700 |
| Body | `Body` 14 | `bodyLarge` 16 / `bodyMedium` 15 / `bodySmall` 13 | Jakarta 500 / 400 |
| Eyebrow (uppercase) | `Eyebrow` 12 | `EyebrowStyle` 12 | Jakarta 700–800, +0.12 em |
| Caption / label | `Caption` 12, `Label` 13 | `labelSmall` 12, `labelMedium` 13, `labelLarge` 15 | Jakarta 500–700 |

---|---|---|---|
| Hero number | 52 | 56 | Mono 700, −0.04 em |
| Display number | 36 | 44 / 34 | Mono 700, −0.03 em |
| Numeral (cards, rings) | 22 | 24 (titleMedium mono) | Mono 700 |
| Editorial title | 20 | 28 (headlineMedium) | Jakarta 800, −0.02 em |
| Title small | 15 | 17 | Jakarta 700 |
| Body | 14 | 15–17 | Jakarta 400/500 |
| Label (uppercase eyebrow) | 12 | 12 | Jakarta 700, +0.12 em, uppercase |
| Caption | 12 | 12–13 | Jakarta 500 |

---

## 3. Shape, depth, spacing

- Cards: 22 dp radius, 1 dp border (`border` token), soft shadow on the phone (elevation 2 dp,
  ambient black 8 %). The watch draws no shadows (black background, cost).
- Hero card: 26 dp radius, a deeper shadow on the phone (6 dp).
- Pills, badges, chips, switches, nav indicator: fully rounded.
- Icon tile: 36 dp (phone) / 32 dp (watch) rounded square (12 / 10 dp radius) in accent-soft with an
  accent-ink icon.
- Spacing: 4-pt grid; phone gutter 16 dp, gap between cards 12 dp; card padding 16 dp
  (hero 18 dp).
- Touch targets ≥ 48 dp everywhere.

---

## 4. Components

| Component | Watch (`app/…/ui/design/`) | Phone (`phone/…/ui/components/`) | Notes |
|---|---|---|---|
| **Hero metric card** | — (the watch hero is the halo number) | `HeroCard` (Template.kt) + Home's `HomeHero` | 26 dp card, uppercase subtitle, big Mono number, status badge pill (`ZoneBadge`: tier-soft fill, tier ink, tier border), wave lines behind |
| **Wave lines** (horizontal) | — | `WaveLines` (shared-ui Visuals.kt) | 3 sine lines with an edge envelope; amplitude from today's dose; alphas 27 % / 12.5 % |
| **Segmented capsule meter** | 24 hourly capsules on Today (`HourBars`) | `CapsuleMeter` (Home hero) | 26 capsules, each in the tier colour of the zone it stands for, last lit one 16 % taller, unlit 14 % shorter, Mono scale marks |
| **Metric triplet** | — | `MetricTriplet` (Today) | Three small cards: uppercase label, Mono value, caption; stacked at font scale ≥ 1.5 |
| **Switch** | Wear M3 buttons/toggles in accent | `VoxSwitch` | 48×28 track, white thumb off, dark thumb on, accent track with a 3 dp glow ring |
| **Row with icon tile** | `MetricCard` + `IconTile` | `GroupRow` / `SwitchRow` + `IconTile` | Tile = rounded square in the tint's soft alpha, icon in the tint |
| **Editorial header** | `EditorialHeader` | `VoxScreen` (`eyebrow`) + `EditorialLabel` | Uppercase label with a pulsing dot (`PulseDot`, shared) over an extra-bold title; both headings |
| **Bottom nav / rail** | — | `PhoneApp` item colours | Muted items; selected: accent-soft pill, accent icon |
| **Bezel arc** | `BezelArc`, `LevelBezel`, `DoseBezel` | — | 270°, rounded caps, 5 dp, track white 7 %, glow = a 3.2× wider stroke at 18 % alpha (no blur) |
| **Harmonic rings + halo** | `WaveHalo` (shared `HarmonicRings`) | — | 3 sine-deformed closed rings (4 finer while listening on headphones) + radial halo |
| **Glow number** | `GlowNumber` | — | Mono number over a translucent radial glow (the template's text-shadow without blur) |
| **Source pill** | `SourcePill` | — | Pulsing dot + uppercase label; on Now it carries the scene / level band |
| **Page dots** | `PageDots` | — | Onboarding progress (the "Step n of m" text stays for TalkBack) |
| **Circular overlay** | `CircularOverlay` | — | Round dark panel with a 1.5 dp tier border: the limit alert |
| **Accent swatches** | — | `AccentSwatches` | 4 round swatches (48 dp targets, radio semantics, colour name as description); selected one scaled 1.22 with a ring |

### Data mapping (no invented data)

| Template visual | Voxara data |
|---|---|
| Phone hero waves | Amplitude from today's dose (`head.dosePercent`) |
| Phone 26 capsules (30–110 dB scale) | Today's dose 0–120 % on Home (marks 0 / 50 / 80 / 100 %, Voxara's percent zones) |
| Phone status badge | Today's dose zone (word + icon) |
| Watch bezel ring | Live level 30–110 dBA on Now / ambient; Hearing Care Score on Home; dose / budget on Today, Week, Headphones |
| Watch harmonic rings | Amplitude and tier from the live level (Now, Home) |
| Watch source pill | The scene / level band Voxara already showed on Now |
| Hourly bars | 24 hourly ledger levels (`DaySummary.hourly`), tier colour per hour |
| Template "max exposure" line | Voxara's own headroom text (`formatHeadroom`) |

---

## 5. Motion and battery (watch)

- Free-running animation (waves, rings, pulsing dot) runs **only** when the screen is interactive
  and the activity is resumed: `LocalAnimate` (not ambient, "remove animations" off) gates it, and
  `animationsActive()` adds the lifecycle (RESUMED). Ambient, paused, or reduced motion → a static
  frame (waves and ring drawn still).
- One `withFrameNanos` clock per visual (`rememberWavePhase`), read only in the draw phase, so a
  frame redraws the Canvas without recomposing; each visual reuses one `Path` (`rewind()`) and its
  `Stroke`s, and rebuilds its halo brush only when the colour changes: no per-frame allocations;
  96 segments per ring.
- No blur anywhere on the watch (minSdk 30): glows are translucent wide strokes / radial gradients.
- **Ambient:** no animation, no glow, no fills, outline-only bezel and number.
- Tiles and complications cannot animate: they get the colours and 270° ProtoLayout arcs only.

## 6. Accessibility

- Touch targets ≥ 48 dp; text ≥ 12 sp; layouts checked at font scale 1.0 / 1.3 (watch) and
  1.0 / 1.3 / 2.0 (phone).
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
