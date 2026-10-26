# Baseline critique and audit (before the redesign)

Method: design review only (detector not applicable, native platform), done in one context from the 117 renders in `before/` (13 screens × 192/216/240 dp × font 1.0/1.3/2.0) plus the source of Home, Now and the design system. Limit: the Roborazzi harness captures the first viewport only, so content below the fold (Home's hero, lower cards) is judged from code. No device or emulator frame data exists.

## Design health (Nielsen, 0-4)
| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 3 | Live level and paused state are shown, but Home's first viewport shows neither level nor dose |
| 2 | Match with the real world | 3 | Plain language zones; "DBA" uppercased; score "96" means nothing without the label below the fold |
| 3 | User control and freedom | 3 | Swipe-back works; all destinations are cards in one list |
| 4 | Consistency and standards | 2 | Left-aligned cards vs centred hero; icon tiles inside pills |
| 5 | Error prevention | 3 | Alerts and calibration safeguards fine |
| 6 | Recognition over recall | 3 | Labels on everything |
| 7 | Flexibility and efficiency | 2 | Level is a tap away from Home; no glance path |
| 8 | Aesthetic and minimalist | 1 | Eyebrows stacked (VOXARA, TONO SAYS, source, uncalibrated), 5 identical cards, footer disclaimer |
| 9 | Error recovery | 3 | Resume, calibration retry exist |
| 10 | Help and documentation | 3 | Tono, "why estimated" |
| | **Total** | **26/40** | Acceptable |

## Audit (native, Android / Wear)
| # | Dimension | Score | Key finding |
|---|---|---|---|
| 1 | Accessibility | 2 | Text clips at font 1.3 and 2.0 ("MONITORIN/G"); time text overlaps content; labels, headings, 48 dp targets are present |
| 2 | Performance | 3 | Good animation gating and no blur; no frame data |
| 3 | Appearance & theming | 3 | Tokenised; dotted-zero mono numerals; one accent family |
| 4 | Platform conformance | 2 | Rectangular full-width cards in a round layout; TimeText collides with content; ScreenScaffold/TLC/EdgeButton used correctly |
| 5 | Adaptivity | 1 | 192 dp × 1.3 and 2.0 break layouts (hero off-screen, title clipped, Now arc off-centre) |
| | **Total** | **11/20** | Acceptable, near Poor |

## Priority issues (user's seven, confirmed in renders)
- **P1 Clipping on round edge.** Home title and status at 192 × 1.3 fill the width; Settings at 216 × 2.0 cuts words ("MONITORIN/G"); cards run into the bottom edge. Fix: round-aware layout and per-scale compact modes.
- **P1 Time text overlaps scrolled content.** Content needs scaffold padding that actually fades under the clock.
- **P1 No hierarchy on Home.** First viewport is two eyebrows and a headline; the dose ring is off-screen at 216 dp (render shows only the top of the ring and "96"). Fix: one hero.
- **P1 Eyebrow overload.** Up to four uppercase labels on one screen. Fix: at most one.
- **P2 "DBA" and dotted zero.** `.uppercase()` on `unit_dba` (`NowScreen.kt:79`, `HeadphonesScreen.kt:69`); the mono zero keeps its dot under every OpenType feature tested (`concepts/numeral_probe.png`).
- **P2 Now arc and bottom card.** Arc is drawn with `maxWidth * 0.92` inside a scrolling list, so it is cut by the column rather than centred on the display; the mode pill is a full-width rectangle.
- **P2 Mixed alignment, long labels.** Left-aligned cards beside centred hero; "0% of today's allowance" wraps.

## What works (keep)
Black AMOLED + purple with amber/crimson zone tiers; the zone as word + icon; the wave rings and halo; the bezel arc idea; tabular, state-driven data; ambient outline view.
