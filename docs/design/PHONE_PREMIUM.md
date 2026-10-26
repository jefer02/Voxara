# Voxara phone — premium pass

The phone (`:phone`) made calm, spacious and fluid: iOS-quality feel, built natively on Material 3 (not a generic
Material look, not an iOS clone). This file holds the tokens, the components and the rules in short form. The values live
in code: `phone/…/ui/theme/PhoneTheme.kt` (colour, type, spacing, radius, motion) and `phone/…/ui/components/`
(`Components.kt`, `Orbit.kt`, `Glass.kt`). DESIGN.md and PRODUCT.md still apply; where this file is stricter, it wins on the phone.

Status: **Phase 1** (theme, shared components, Home). Every other screen gets the new theme and shared components for
free but is not redesigned yet. Renders: `docs/design/phone-premium/` (`before/`, `after/`, `compare_home_*.png`), made by
`PremiumCaptureTest` (opt-in, `VOXARA_PREMIUM_DIR`).

## Rules (short form)

- **Not a medical device**; estimates are labelled next to their number (the uncalibrated note lives in the dose card).
- **Numbers are never invented**: every figure is the watch's, the ledger's or derived from them (`HearingFigures`).
  Animation never shows a value that is not on the way to the real one (the ring's spring is clamped to 0..100 %).
- **Zone = colour + word + icon**, always. Zone colours (amber, crimson) only where they carry a zone.
- **WCAG AA** for all text; targets ≥ 48 dp; text ≥ 12 sp; layouts hold at font 1.0, 1.3 and 2.0, EN and ES.
- **Strings in EN and ES**; unit `dBA`; no uppercase labels; no ellipsis on content text.
- **One hero per screen.** Home's hero is the dose card.
- **Brand palette, not dynamic colour.** Light and near-black dark, the chosen accent (violet by default).
- **minSdk 28.** Best on Android 13+; below Android 12 no blur; reduced motion removes all non-essential motion.

## Colour

Unchanged values (`shared/…/core/design/Palette.kt`, contrast enforced by `AccentPaletteTest`, `ChartColorsTest`).

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#F3F5F9` | `#090B10` (near-black) | Screen ground, glass tint |
| `surface` | `#FFFFFF` | `#131722` | Grouped sections, cards, the hero |
| `surface2` | `#EBF0F7` | `#1B2030` | Insets inside a surface (chips, tracks) |
| `outline` | `#0F172A` 7 % | white 8 % | Hairlines only (row dividers, bar edge). Sections have **no border** |
| `text` / `text2` / `text3` | `#0F172A` / `#334155` / `#5B677A` | `#F8FAFC` / `#AEB6C4` / `#8A94A6` | Primary / secondary / chevrons |
| accent (Purple) fill · ink | `#8B5CF6` · `#6D28D9` | `#8B5CF6` · `#A78BFA` | Chrome: links, selected tab, switches |
| level tones | accent · orange `#D56E00` (text `#944D00`) · crimson | accent · amber `#FFB800` · crimson | Only what shows a dose or level |

Light-mode zones, each checked as a mark (≥ 3:1 on background and card) and as text (≥ 4.5:1 on every surface and its
soft tint), and ≥ 6 apart for colour-blind readers (`AccentPaletteTest`, `ChartColorsTest`, thresholds untouched):

| Zone | Mark (ring, icon) | Text (zone word) | Mark on card · on page | Text, worst case |
|---|---|---|---|---|
| OK, Moderate | the accent fill (Purple `#8B5CF6`) | accent ink `#6D28D9` | 4.23 · 3.88 | 5.23 |
| Loud | **`#D56E00`** vivid orange, hue 31° (was `#B7791F`, a muddy brown) | **`#944D00`**, same hue (was `#92400E`) | 3.47 · 3.18 | 4.70 |
| Dangerous | `#FF2A6D` | `#9F1239` | 3.62 · 3.31 | 5.71 |

Redder oranges (hue < 29°) came within 6 of the crimson for tritanopia readers; 31° is the most vivid hue that stays
7.4 apart from every accent and the crimson. The other accents (Turquoise, Blue, Emerald) pass the same checks.

Neutral notes (uncalibrated) are `text2`, never amber (as on the watch).

## Type

Plus Jakarta Sans everywhere (`VoxTypography` + `VoxType` roles). Figures are drawn **one fixed-width cell per digit**
(`RollingDigits`, `TabularDigits`, the new `CountingDigits`): tabular without the `tnum` feature, whose alternate "1" has
a slab foot (DESIGN.md, Plain Zero Rule).

| Role | Size / line | Weight | Where |
|---|---|---|---|
| `VoxType.largeTitle` | 32 / 38, −0.025 em | 800 | The screen title, first list item |
| `VoxType.barTitle` | 17 / 22 | 700 | The collapsed bar (held at 1.3×) |
| Hero figure | 72 → 40 (fits the ring) | 800 | `FitFigure`, held at 1.3× |
| Tile figure | 36 → 26 | 800 | `ShareTile` |
| `VoxType.sectionHeader` | 15 / 20 | 700, `text2` | Above a grouped section |
| `titleSmall` | 15 / 20 | 700 | Row titles |
| `bodyMedium` / `bodySmall` | 15 / 21 · 13 / 18 | 400 | Text, captions |
| `labelMedium` | 13 / 16 | 600 | Tab labels (held at 1.3×), links in rows |

## Spacing and radius

8-pt grid (`VoxSpace`): **4 · 8 · 12 · 16 · 24 · 32 · 48** (4 and 12 only inside rows). Screen gutter 16; list gap 16;
section header 8 above its section; row padding 16 × 12; hero padding 24.

Radius (`VoxRadius`): rows 12 · sections and cards 22 · hero 28 · pills full. Row min height 52 dp; icon tile 32 dp;
dividers inset to the text (16 dp, or 60 dp past an icon tile).

## Motion

Two families, as Material 3 Expressive defines them (`VoxMotion`). `MotionScheme.expressive()` is internal in the stable
material3 1.4 this app builds on, so the specs carry the same token values and switch to the real scheme in one place
when it becomes public.

| Spec | Spring (damping, stiffness) | Used for |
|---|---|---|
| `heroSlow` (expressive) | 0.8, 200 | Ring fill; a card growing into its screen (shared bounds) |
| `hero` (expressive) | 0.8, 380 | A row expanding in place |
| `standard` | 0.9, 700 | Chevron turning, pills |
| `standardFast` | 0.9, 1400 | Press feedback (97 %) |
| `effects` | 1.0, 1600 | Alpha / colour |
| Count | 900 ms, decelerating | Figures counting to their value |
| Entrance | 420 ms, 45 ms stagger, 12 dp rise | First load of a screen only, once per process |

- Never animate everything: hero moments only (ring, count, card → screen); the rest is standard or still.
- **Shared elements**: Home dose card → Today (`SharedKeys.DOSE`); Home headphones card → Headphones
  (`SharedKeys.HEADPHONES`). Predictive back on (`enableOnBackInvokedCallback`); Navigation Compose scrubs the pop.
- **Reduced motion** (system "remove animations"): every spec snaps, no count, no entrance, no shared bounds.
- **Performance**: animated values are read in `graphicsLayer` / `drawBehind` (ring sweep, entrance, press, bar
  material, sync spin); a counting figure recomposes only itself and only when its integer changes; lazy items have keys.

## Materials

The top bar and the bottom navigation are glass: content scrolls under them, blurred (Haze 1.7.2, RenderEffect, 24 dp
blur) under a 78 % tint (the background for the top bar, the surface for the tab capsule; AA for any text on top). The top bar's material, hairline and small title fade
in as the large title scrolls under it (read in the draw phase). **Fallback**: below Android 12, on low-RAM devices and in
previews, the bars are the solid background: nothing depends on the blur to be read.

## The tab bar (floating capsule)

A fully rounded capsule floating 16 dp from the sides and 8 dp above the gesture area (`CapsuleTabBar`, `PhoneApp.kt`):
64 dp tall (taller only if large text needs it), at most 480 dp wide, glass over the content (solid surface where there is
no blur), a 1 dp hairline border, no indicator pill. Selected tab: filled icon (`TabIcons`) in the accent, with its label
in the accent; the others: outlined icon (`VoxIcons`) and label in `text2`. The change springs (standard: the colours
cross, the icons cross-fade, the selected icon rises 2 dp) and gives one `SEGMENT_TICK`. From font scale 1.15 only the
selected tab shows its label (it takes a wider share; labels held at 1.3×); every tab keeps its name for TalkBack, as a
tab with its selected state. The content's bottom padding is the capsule's measured height plus its gap and the gesture
area (`LocalBottomBarInset`), so the last card always ends above it. Wide windows keep the rail.

## Haptics

Crisp ticks only (`VoxHaptics`, `HapticFeedbackConstants`): switches `TOGGLE_ON/OFF` (Android 14+, else `CLOCK_TICK`),
segmented controls, steppers and the tab bar `SEGMENT_TICK`, chart scrubbing `SEGMENT_FREQUENT_TICK` (Phase 2). Nothing buzzy.

## Edge to edge

Every screen draws behind the system bars. `VoxScreen` pads the list by the status bar + bar height at the top, and by
the bottom bar (`LocalBottomBarInset`: its measured height including the gesture area) or the navigation bar at the
bottom, plus 32 dp, so the last item ends above the bar. Display cutouts are padded sideways. A screen's own bottom bar
(Tono's field) sits on top of the app's bar; the snackbar too.

## Components

| Component | File | What it is |
|---|---|---|
| `VoxaraPhoneTheme` | `theme/PhoneTheme.kt` | Colours (crossfade), type, shapes, `LocalReduceMotion`, `LocalGlass` |
| `VoxScreen` | `Components.kt` | Large collapsing title (list item) + `GlassTopBar`, pull to refresh, insets |
| `VoxNavigationSuite`, `CapsuleTabBar`, `TabIcons` | `PhoneApp.kt`, `TabIcons.kt` | Floating glass capsule on phones (filled icon = selected), rail on wide windows |
| `HeroCard` | `Components.kt` | The one hero container: 28 dp, press feedback, optional shared key |
| `Group`, `GroupHeader`, `GroupDivider`, `GroupNote` | `Components.kt` | Inset grouped section |
| `GroupRow`, `SwitchRow`, `StepperRow`, `ChoiceRow`, `ExpandableRow` | `Components.kt` | Rows (52 dp, haptics) |
| `VoxCard`, `Banner`, `EmptyState`, `SkeletonCard` | `Components.kt` | Borderless cards and states |
| `DoseRing` | `Orbit.kt` | The watch's Dose ring: one clean arc from 12 o'clock, clockwise (round caps, no glow or halo) over a faint full track, full = 100 % |
| `FitFigure(count = true)` / `CountingDigits` | `Orbit.kt` | A figure that fits and counts in fixed cells |
| `ShareTile` | `Orbit.kt` | A weekly share card (icon, label, figure, caption, meter), optional shared key |
| `entrance`, `sharedAcrossScreens`, `SharedKeys` | `Orbit.kt` | First-load stagger; container transform |
| `glassBar`, `glassSource`, `VoxHaptics` | `Glass.kt` | Glass with fallback; crisp ticks |

## Home (Phase 1)

Large title "Inicio" with the watch's line (name, last report, situation + headphones layer; tap → Watch) and the sync
button in the bar. Then: the **dose card** (ring with the counting figure and "del límite de hoy", the zone word and
icon, then "Sin calibrar · ±3–5 dB" with Calibrar on one line (wraps to two at most at font 2.0, never cut); the whole card opens Today, no chevron) · Tono's sentence as one row (→ Tono) · "Últimos 7 días":
Entorno and Audífonos cards (→ Trends, → Headphones) · "Hoy, hora por hora" (→ Today) · "Última alerta" (→ Alerts).
Live headphone cards, stale and battery banners keep their place. Wide windows: two panes.

## Later phases (not started; waiting for approval)

Trends (scrubbable smooth charts with value bubble and ticks), the other screens on the grouped layout, a Baseline
Profile (baselineprofile plugin + a small macrobenchmark module covering start-up, Home and Trends scroll).
