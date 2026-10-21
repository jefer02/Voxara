# Pass 2: critique and audit

Method: design review only (detector not applicable, native platform), by the author, from fresh renders of every watch screen (`after/`: 192/216/240 dp × font 1.0/1.3/2.0, English and Spanish, plus each page below its hero), the frame strips in `motion/`, and the debug build on the Wear emulator. A self-assessment, not an independent pass. `before/` is the pass-1 build; `compare/` puts them side by side; `pages_216.png` and `pages_es_216.png` show the six pages together.

## What the reviewer found in pass 1 (and what was done)

| # | Finding | Severity | Fix |
|---|---|---|---|
| 1 | Five pages with the same hero (number, unit, pill, waves, grey ring); Home and Now both showed the live level; Today/Week/Headphones all "0 %" | P1 | Each page has its own picture on the ring and its own centre (see below) |
| 2 | Outlined zone pill with a check over the brightest waves: button-like, low contrast | P1 | `ZoneTag`: a small filled tag (tier tint over a dark scrim), icon + word, no outline; contrast test over the brightest wave colour |
| 3 | Wave rings only filled the middle | P2 | Rings now fill the inside of the ring up to its inner edge; lower opacity (×0.9) plus a radial scrim behind the text; still follow the live level |
| 4 | "Uncalibrated estimate" in amber (the Loud colour) | P1 | Neutral secondary text with an info icon (`FootNote`); amber and crimson only for zone warnings |
| 5 | TimeText over scrolling content on Now and on the last page | P1 | Black edge scrims fade content out under the clock (and under the page indicator); hero pages keep the scrim off at rest |
| 6 | Last page: legal text clipped and under the indicator; Ask edge button wrapping under the dots | P1 | Edge button replaced by a normal "Ask" button in the list; bottom inset + scrim for the indicator; label shortened ("Preguntar") |
| 7 | Scene word floating alone | P2 | A "Surroundings" row (label + scene), then the mode chip |
| 8 | "Entorno 0% · Auriculares / 0%", "esta semana, est." | P2 | Two short lines; no abbreviations ("estimated" / "estimado" spelled out) |
| 9 | "54.0 dBA" in Spanish | P1 | `formatDecimal` / `formatTwa(locale)` use the app's language; dates and the tile's time too (the app language is a configuration context, not `Locale.getDefault()`) |
| 10 | "8-hour equivalent: 54.0 dBA" | P2 | "Same as 54.0 dBA for 8 hours" / "Como 54,0 dBA durante 8 horas" |
| 11 | Page indicator overlapping content | P1 | Hero pages leave the gap to the name and foot; lists have a bottom inset and scrim |
| 12 | Detection log among scattered debug entries | P2 | One collapsed "Developer tools" section at the end of Settings, debug builds only (see PROGRESS.md) |

## How each page is now distinct

| Page | Picture on the ring | Centre | Accent |
|---|---|---|---|
| Overview | today's dose, ticked at 50/80/100 % | Tono's line, the live level, the zone | violet |
| Now | the dBA scale 30-110 as four zone segments that light up to the level, with a marker | big rolling dBA, zone | the zone's tier colour (name too) |
| Today | 24 hourly bars from the ledger, noon at the top, a marker on this hour | dose %, "of your allowance", zone | violet |
| Week | seven segments, one per day, filled to that day's share, in that day's zone colour | worst weekly %, zone | violet |
| Headphones | indigo scale with the confidence range as a band around the marker (or the week's share when not listening) | ≈ estimated dBA, "estimated", zone | indigo |

Home and Now stay separate on purpose: Home answers "am I fine today?" with a sentence and the dose; Now answers "how loud is it?" with the number and a scale. Merging them would have lost Tono's line or the scale.

## Design health (Nielsen, 0-4)
| # | Heuristic | Pass 1 | Pass 2 | Note |
|---|---|---|---|---|
| 1 | Visibility of system status | 4 | 4 | Zone tag keeps its contrast over the waves; paused still a resume pill |
| 2 | Match with the real world | 3 | 4 | "Same as 54,0 dBA for 8 hours", decimal comma, no abbreviations |
| 3 | User control and freedom | 3 | 3 | Unchanged |
| 4 | Consistency and standards | 3 | 4 | One page grammar (picture, figure, tag, name above the dots); lists share the scrim/inset rules |
| 5 | Error prevention | 3 | 3 | Unchanged |
| 6 | Recognition rather than recall | 3 | 4 | Every page has an icon + name and its own picture; TalkBack hears "Page 3 of 6, Today" |
| 7 | Flexibility and efficiency | 3 | 3 | Tono's line still opens Ask |
| 8 | Aesthetic and minimalist design | 3 | 3 | Centres are lean; Now's foot is still up to three lines at 216 dp |
| 9 | Error recovery | 3 | 3 | Unchanged |
| 10 | Help and documentation | 3 | 3 | Unchanged |
| | **Total** | **31/40** | **34/40** | Good |

## Audit (native, Android / Wear)
| # | Dimension | Pass 1 | Pass 2 | Note |
|---|---|---|---|---|
| 1 | Accessibility | 3 | 3 | Page names/descriptions added, contrast tests extended (indigo, tag over waves); figure still capped at 1.3×; no TalkBack run on a device |
| 2 | Performance | 3 | 3 | The pictures reuse cached strokes and colour arrays (no per-frame allocation); the waves are the only free-running clock; no frame data from a watch |
| 3 | Appearance and theming | 3 | 4 | Tokens only; amber/crimson reserved for zones; indigo for headphones |
| 4 | Platform conformance | 3 | 3 | Pager + indicator, scaffolds, transforming lists; no edge button under the indicator |
| 5 | Adaptivity | 3 | 3 | 192/216/240 dp × 1.0/1.3/2.0 and Spanish: no clipping; the compact arrangement drops caption and foot |
| | **Total** | **15/20** | **16/20** | Good |

## Open items
- Names sit above the page dots in the ring's gap: correct, but less prominent than a title at the top (the ring crosses the top of the display).
- At 1.3× and above Tono's line on Overview is cut at two lines; the full text is on Ask.
- The Today ring shows only measured hours; a quiet day reads as dots.
