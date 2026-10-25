# Audit after the redesign (native, Android): phone app

Code-level audit against `android.md` and the Material 3 rules, same method as `baseline-audit.md`; the 305 renders in `after/` and three
test suites (`FontScaleLayoutTest`, `ChartColorsTest`, `FiguresConsistencyTest`) are the evidence. Not a design critique (see `after-critique.md`).

## Audit Health Score

| # | Dimension | Before | After | Key finding |
|---|-----------|:-----:|:-----:|-------------|
| 1 | Accessibility | 3 | 4 | Large text is a test (no broken word, cut-off or overlap on 16 screens at 1.3 and 2.0). Ring, tiles and charts are spoken as single items with values; targets ≥ 48 dp; state spoken on expandable rows; contrast tested for every accent × theme including charts and calendar cells and colour-blind distance. Left: no touch-explore of single chart points. |
| 2 | Performance | 3 | 3 | Charts draw in through draw-phase state (no recomposition per frame); the wave clock runs only while pulling or syncing. Column charts build a `Path` per mark per draw frame while drawing in (24 or 7 small paths: acceptable, noted); `animateVox` recomposes the theme subtree for 360 ms on a theme change. |
| 3 | Appearance & theming | 3 | 4 | One token set; colours crossfade on theme and accent change; the four accents × two themes pass the WCAG and the chart tests (Emerald's dark fill moved to `#34D399` to stay apart from crimson for deuteranopia). Dynamic colour stays off on purpose. |
| 4 | Platform conformance | 2 | 3 | The hand-drawn switch is now the Material 3 `Switch`; the filter is the M3 segmented button (radio rows when a word will not fit); uppercase eyebrows are gone; navigation suite, large collapsing app bar, snackbar, pull to refresh, shared-element transition, predictive-back-compatible navigation. Left: inset-grouped list rows on Settings and Watch. |
| 5 | Adaptivity | 2 | 4 | Bar → rail by window size class; Home and Trends are two panes from 840 dp; every control stacks or becomes a list at large text. Left: Today, Alerts, Settings, Watch are one centred column on a tablet. |
| | **Total** | **13/20** | **18/20** | **Good** |

## Platform conformance verdict

**Pass.** It reads as an Android app throughout: Material 3 navigation suite (four items), `LargeTopAppBar`, `SegmentedButton`, `Switch`, `AssistChip`,
`PullToRefreshBox` (with a branded indicator), `SharedTransitionLayout` with `sharedBounds` for the ring, system insets, `NavHost`
transitions that respect "remove animations". The one reservation is the inset-grouped lists, which are consistent with each other and with the
watch's grouped rows and are left as they are.

## Executive summary

- Audit Health Score **18/20** (Good).
- Issues: 0 P0, 0 P1, 2 P2, 2 P3 (the baseline had 4 P1, 5 P2, 3 P3).
- Remaining: (1) chart points are not touch-explorable; (2) inset-grouped lists; (3) one-column Today/Settings on tablets; (4) Path allocation while charts draw in.
- Next steps: dataviz interaction layer (tap a column), list-detail for Settings, then re-audit.

## Detailed findings

**[P2] No touch readout on charts.** Location: `Charts.kt` (`HourColumns`, `DayColumns`, `LineChart`). Category: Accessibility. Impact: a sighted touch user cannot ask a column for its value (the week's seven are printed, the hours show the loudest); TalkBack hears the whole description at once. Recommendation: per-mark semantics (`CollectionInfo` rows or `customActions`) and a drag-to-read tooltip. Command: `$impeccable delight`.

**[P2] Settings is one 4,200 dp column.** Location: `SettingsScreen.kt`. Category: Adaptivity. Impact: on a tablet a master-detail would fit; on a phone it is long. Recommendation: `ListDetailPaneScaffold` (the `adaptive-layout` library is not a dependency yet). Command: `$impeccable adapt`.

**[P3] Inset-grouped lists.** Location: `Group`, `GroupRow` in `Components.kt`. Category: Platform conformance. Impact: iOS-flavoured grouping. Recommendation: none now. Command: n/a.

**[P3] Path allocation while a chart draws in.** Location: `Charts.capped`. Category: Performance. Impact: a few small objects per frame for 650 ms, once per screen. Recommendation: reuse one `Path` per chart if profiling ever shows it. Command: `$impeccable optimize`.

## Fixed since the baseline (the baseline's P1/P2 list)

| Baseline finding | Now |
|---|---|
| Status text truncated at font 2.0 | Status wraps; `FontScaleLayoutTest` forbids cut-off text and broken words |
| Numerals draw a dotted zero and use `tnum` | Plus Jakarta Sans digit cells (`DigitCells.kt`), no font feature; the watch's `Numerals.kt` is now a wrapper of the same code |
| Home's first viewport had no dose figure | The ring |
| Adaptive layout is a width cap | Two panes from 840 dp (Home, Trends), rail below |
| Charts: dashed guides, full-slot bars, no value readout | Solid hairlines, capped thin columns, values on the week's columns, the loudest hour named (touch readout still open) |
| Uppercase eyebrows, hand-drawn switch | Gone; M3 `Switch` |
