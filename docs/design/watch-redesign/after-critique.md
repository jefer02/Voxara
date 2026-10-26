# After the redesign: critique and audit

Method: design review only (detector not applicable, native platform), done in the same context that built it, from the renders in `after/` (every screen, 192/216/240 dp, font 1.0/1.3/2.0, plus the pages below their heroes), the animation frame strips in `motion/`, and a run of the debug build on a Wear OS emulator (API 37, 480×480). **This is a self-assessment, not an independent pass**; scores are comparable with `baseline-critique.md` only in that they use the same scales and the same author.

## Design health (Nielsen, 0-4)
| # | Heuristic | Before | After | Note |
|---|---|---|---|---|
| 1 | Visibility of system status | 3 | 4 | Level, dose, zone, paused and estimate flag are in the first viewport of Home / Now |
| 2 | Match with the real world | 3 | 3 | Zone words and plain sentences; "dBA" now always in that case |
| 3 | User control and freedom | 3 | 3 | Swipe back on the first page leaves; pages by swipe |
| 4 | Consistency and standards | 2 | 3 | One hero grammar on five pages; the list screens are still rows of buttons and cards |
| 5 | Error prevention | 3 | 3 | Unchanged |
| 6 | Recognition rather than recall | 3 | 3 | Hero pages carry no page title (the unit line and the dots tell them apart): a learnable pattern, not a labelled one |
| 7 | Flexibility and efficiency | 2 | 3 | Glance first, detail by scroll, Ask one tap from Home |
| 8 | Aesthetic and minimalist design | 1 | 3 | No uppercase labels, one hero per page; Now's foot is up to three lines at 216 dp |
| 9 | Error recovery | 3 | 3 | Unchanged |
| 10 | Help and documentation | 3 | 3 | Unchanged |
| | **Total** | **26/40** | **31/40** | Good |

## Audit (native, Android / Wear)
| # | Dimension | Before | After | Note |
|---|---|---|---|---|
| 1 | Accessibility | 2 | 3 | No clipping in the renders at 1.3 and 2.0; figure capped at 1.3×; heroes spoken as one description; zone badges are 48 dp only when they act (resume pill). Not tested with TalkBack on a device |
| 2 | Performance | 3 | 3 | Same animation gating, no blur; swiping all six pages on the emulator: 2.9 % janky, 99th percentile 65 ms (idle rings: 0 missed vsync). Emulator, not a watch |
| 3 | Appearance and theming | 3 | 3 | Tokens only; JetBrains Mono no longer used for watch numerals |
| 4 | Platform conformance | 2 | 3 | Pager + page indicator, ScreenScaffold, TransformingLazyColumn, EdgeButton; a few custom pills (mode chip, resume pill) remain |
| 5 | Adaptivity | 1 | 3 | 192/216/240 dp × 1.0/1.3/2.0 fit; the 192 dp hero and large fonts use the compact arrangement |
| | **Total** | **11/20** | **15/20** | Good |

## The seven problems
1. **Clipping at the round edge:** fixed in the renders at all 9 size/scale pairs. Long text moves below the fold or wraps; the figure is capped.
2. **TimeText over content:** pages sit inside `ScreenScaffold`; list items scale and fade under the clock; heroes keep the top band clear.
3. **Monotonous card stack / no hierarchy:** every glance page is one hero with its value on the arc; cards survive only in lists below.
4. **Eyebrow overload:** none left on the watch (group headings in Settings are sentence-case headings).
5. **"DBA" and dotted zero:** `dBA` everywhere; numerals are Plus Jakarta Sans in fixed-width digit cells (`concepts/numeral_probe.png`, `after/ds_numerals_*`).
6. **Now arc and bottom card:** arc concentric with the display; the mode chip is a pill below the fold.
7. **Mixed alignment and wrapping labels:** hero pages are centred; the "of today's allowance" line is short; foot copy was shortened ("1h 30m left · 46% of today").

## Open items
- Hero pages have no written page name; if testing shows people lose track, put the page name in the unit line.
- The pager's indicator can overlap the last lines of a list page while it is visible (it fades after a moment).
- Tono's one line is cut at two lines on the hero; the full text is on Ask.
