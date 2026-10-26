# Baseline audit (native, Android): phone app before the redesign

Code-level audit against `android.md` and the Material 3 rules; source read in `phone/src/main/java/.../ui/**` with the 280 renders
in `before/` as evidence for the layout findings. Not a design critique (see `baseline-critique.md`).

## Audit Health Score

| # | Dimension | Score | Key finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | 3 | Labels, headings, roles and chart descriptions are in place; at font 2.0 status text is ellipsised and a hero label wraps into its figure. |
| 2 | Performance | 3 | `LazyColumn` everywhere; a free-running wave clock in the hero (stopped for reduced motion); no per-frame allocation seen. |
| 3 | Appearance & theming | 3 | Semantic roles and a tested 4 accents × 2 themes palette; dynamic colour deliberately off (a product decision: zones must not follow the wallpaper). |
| 4 | Platform conformance | 2 | Inset-grouped list rows, a hand-drawn switch and uppercase tracked eyebrows are off-Material; everything else (navigation suite, large top bar, snackbar, pull to refresh) is native. |
| 5 | Adaptivity | 2 | Bottom bar to rail works; content is a centred 720 dp column at every width, no two-pane, no use of the extra space on tablet or unfolded foldable. |
| **Total** | | **13/20** | **Acceptable**: significant work needed |

## Platform conformance verdict

**Pass with reservations.** It reads as an Android app (navigation suite, large collapsing top bar, snackbars, system back, edge to
edge with insets, pull to refresh) but several surfaces borrow from iOS or from a web template:

- `Group` / `GroupRow` / `GroupHeader` are iOS "inset grouped" lists (a rounded white group, hairline dividers, a small uppercase
  header). Material uses `ListItem` on a surface and sentence-case section titles.
- `VoxSwitch` is a hand-drawn 48 × 28 switch (the row around it is the toggleable target, so it is accessible, but it is not the M3 `Switch`).
- `EyebrowStyle` / `EditorialLabel`: uppercase, letter-spaced labels with a pulsing dot, on almost every screen. Not an M3 role,
  against the product's own "no uppercase labels" rule on the watch, and 7 call sites upper-case the string itself.
- `ChoiceRow` uses `SingleChoiceSegmentedButtonRow` correctly, but with custom colours that make "Day | Week | Month" very heavy.

## Executive summary

- Audit Health Score **13/20** (Acceptable).
- Issues: 0 P0, 4 P1, 5 P2, 3 P3.
- Top issues: (1) status text ellipsised at font 2.0, (2) numerals are Mono with the dotted zero and `tnum`, against the watch, (3) the tablet
  layout is a stretched phone, (4) charts are not described by value (only a summary) and use dashed guides, (5) off-Material list and switch.
- Next steps: shape the new Home, typeset (numerals), adapt (two-pane), then re-audit.

## Detailed findings

**[P1] Status text truncated at font 2.0.** Location: `HomeScreen.LinkChip` (`maxLines = 1`, `TextOverflow.Ellipsis` on the link name),
`EditorialLabel` (`maxLines = 1`, ellipsis) in `Components.kt`. Category: Accessibility. Impact: "Galaxy Watch7 · lin…" loses the word that says
whether the link works. Guideline: text must not be clipped at the largest font setting (WCAG 1.4.4 and Android large-text guidance).
Recommendation: wrap, never ellipsise status. Command: `$impeccable adapt`.

**[P1] Display numerals draw a dotted zero and use `tnum`.** Location: `PhoneTheme.kt` (`MonoFamily` + `TABULAR` on `displayLarge/Medium/Small`,
`VoxNum`). Category: Conformance with the product's own system (STYLE.md §2, the watch Numerals). Impact: "10", "30", "0%" read differently from
the watch; `tnum` on Plus Jakarta Sans would give "1" a slab foot. Recommendation: fixed-width digit cells in Plus Jakarta Sans
(`Numerals.kt`, moved to `:shared-ui`). Command: `$impeccable typeset`.

**[P1] Home's first viewport carries no dose figure at font 2.0 and a secondary figure at 1.0.** Location: `HomeScreen.HomeHero`.
Category: Conformance / Adaptivity. Impact: the purpose of the screen is missed (see critique). Command: `$impeccable shape`.

**[P1] Adaptive layout is a width cap, not an adaptive layout.** Location: `Components.kt` `ContentMaxWidth = 720.dp`, `contentWidth()`;
`PhoneApp.kt` `NavigationSuiteScaffold`. Category: Adaptivity. Impact: 1280 dp tablet shows 500 dp of empty canvas; foldable (673 dp)
is a phone with margins; no list-detail or two-pane. Guideline: Material adaptive layouts (window size classes, canonical layouts).
Recommendation: size-class driven two-pane Home and Trends; navigation rail already switches. Command: `$impeccable adapt`.

**[P2] Charts: dashed guides, full-slot bars, no value readout.** Location: `Charts.kt` (`PathEffect.dashPathEffect` in `HourlyChart`,
`DoseBars`, `BudgetLine`; bars `bw` fills the slot). Category: Conformance / Accessibility. Impact: reads loud, value only through the content description.
Recommendation: dataviz mark specs, tap-to-read, direct label on the extreme. Command: `$impeccable layout`.

**[P2] Hero wave clock runs whenever Home is resumed.** Location: `HeroCard` (`rememberWavePhase`). Category: Performance. Impact: a free
frame clock on the first screen of the app for decoration; stopped only for reduced motion. Recommendation: keep one subtle clock only
where it carries data (the ring's breathing) and stop it off-screen. Command: `$impeccable optimize` (during polish).

**[P2] Inset-grouped lists and the hand-drawn switch are off-Material.** Location: `Components.kt` `Group`, `GroupRow`, `SwitchRow`, `VoxSwitch`.
Category: Conformance. Recommendation: `ListItem` rows on a surface, M3 `Switch` (themed), sentence-case section titles. Command: `$impeccable polish`.

**[P2] Uppercase strings in code (7 call sites) and tracked eyebrows.** Location: `EditorialLabel`, `GroupHeader`, `MetricTriplet`, `HomeHero`. Category:
Conformance with the product rule. Command: `$impeccable typeset`.

**[P2] Touch targets rely on defaults.** Location: `StatCard`, `GroupRow` (clickable rows are ≥ 48 dp by padding, verified in the renders), `PendingTag` (not
interactive). No violation found in the renders; the swatch, the stepper buttons and the chips are 48 dp. Recorded as verified, not a defect.

**[P3] No `android:enableOnBackInvokedCallback` in the manifest.** Location: `phone/src/main/AndroidManifest.xml`. targetSdk 37 makes the
predictive back animation the default, so nothing is lost; recorded for completeness.

**[P3] Dynamic colour is off.** Deliberate (zones and accent are information). Documented in `PhoneTheme.kt` and STYLE.md.

**[P3] `Updated … ago` repeated per block.** One freshness line per screen is enough.

## Patterns and systemic issues

- One component vocabulary (`VoxCard`, `Group`, `StatCard`) carries every screen, so every screen is the same stack of white cards.
- Mono numerals and uppercase eyebrows are the phone's "template" signature and are the main difference from the watch.
- Large text is handled per screen (`MetricTriplet` stacks itself) rather than by a shared rule.

## Positive findings

- Contrast of every accent × theme is covered by `AccentPaletteTest`; the zone tiers pass on all surfaces.
- Every chart has a spoken description; headings carry semantics; zones are word + icon.
- `reduceMotion` follows the system animator scale; springs and waves stop.
- Edge to edge with `enableEdgeToEdge`, insets via `Scaffold`; `NavigationSuiteScaffold` handles bar versus rail.
- `LazyColumn` for every list; pull to refresh via `PullToRefreshBox`.
