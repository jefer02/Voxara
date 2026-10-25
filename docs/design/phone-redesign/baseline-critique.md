# Baseline critique: phone app before the redesign

Method: design review only (detector not applicable, native platform). Surface brief: `SURFACE_BRIEF.md` (mode Operate).
Evidence: the 280 renders in `before/` (phone 360 dp, foldable 673 dp, tablet 1280 dp; font 1.0 / 1.3 / 2.0; light and dark) and the
source of every screen. Fake data (an afternoon that reached LOUD), so what the renders show is layout and hierarchy, not real figures.

## Design Health Score

| # | Heuristic | Score | Key issue |
|---|-----------|-------|-----------|
| 1 | Visibility of system status | 3 | The link, its freshness, "pending sync" and the sync problem states are all honest and reachable. Home shows the same sync line twice (eyebrow and chip). |
| 2 | Match system / real world | 2 | The hero's biggest figure is a "Hearing care score" (84) that exists nowhere else in the product; the figure the watch is built on, today's dose, is 3rd on the page. "7-day budget", "dose" and "allowance" are used for the same idea. |
| 3 | User control and freedom | 3 | Back everywhere, pull to refresh, confirmation before delete. Today / Alerts / Headphones are reachable only through rows at the bottom of Home or Settings. |
| 4 | Consistency and standards | 2 | Three visual languages at once: the watch's Orbit world (ring, plain numerals), the phone's template world (Mono numerals with a dotted zero, uppercase letter-spaced eyebrows, shadowed cards) and Material 3. The same percentage appears as a ring, a capsule meter and a bar. |
| 5 | Error prevention | 3 | Steppers are bounded, destructive actions confirm, opt-ins start off. |
| 6 | Recognition rather than recall | 3 | Five labelled destinations. Three hubs of "see more" rows on Home repeat what the tab bar already offers. |
| 7 | Flexibility and efficiency | 2 | No quick path from the verdict to the cause; on a tablet the app is one 720 dp column in the middle of 1280 dp, with the rail far away. |
| 8 | Aesthetic and minimalist design | 2 | Home is nine stacked blocks. Everything is the same white rounded card with a shadow, so nothing leads. Uppercase tracked eyebrows over almost every block. |
| 9 | Error recovery | 3 | Each Watch link state has a plain explanation and the one action that helps. |
| 10 | Help and documentation | 3 | How it works, setup, troubleshooting and calibration guidance exist and are in context. |
| **Total** | | **26/40** | **Acceptable**: significant improvements needed |

## Design specificity verdict

**LLM assessment.** The pieces are Voxara's (zone words with icons, the capsule scale, the accent that reacts to the zone), but the
composition is the generic "health app dashboard": a greeting hero, a stack of identical stat cards each with a ring, a list of
"see more" rows. The watch got a distinctive world in the Orbit redesign (one ring, one figure, one word); the phone shows none of
it. Put the two side by side and they are two different products. The phone's strongest asset, that it knows the week and the history,
is spread over so many equal cards that none of it reads as an answer.

**Deterministic scan.** Not applicable (native platform).

## Overall impression

It works and it is honest, but it does not answer "how is my hearing today?" in the first screen. The single biggest opportunity: make
Home one figure, one word, one sentence, drawn on the same ring as the watch, and let everything else be one tap away or below.

## What's working

- **Honesty about state.** Freshness, "as of", the pending-sync tag, the link health text and the uncalibrated banner are exactly the
  right level of candour for a hearing-protection tool, and they are consistently worded.
- **Colour is never alone.** Every tier colour comes with a word and an icon (`ZoneBadge`), the charts have spoken descriptions, and
  the accent-by-zone idea (amber, crimson only for warnings) is sound.
- **A solid base to build on.** Semantic colour tokens for four accents × two themes, tested for contrast; adaptive navigation suite;
  skeletons, empty states and every link state exist; strings in EN and ES.

## Priority issues

**[P1] Home does not answer the question in the first viewport.**
- *Why it matters:* at 360 × 800 dp and font 1.0 the first screen is a link chip, a headline sentence from Tono, a made-up score (84)
  and a capsule meter with no value on it; today's real figure (83 %, Loud) is a third of the way down. At font 2.0 the headline takes
  three lines and the figure is gone from the screen. The glance scene (queue, train, one hand) fails.
- *Fix:* lead with today's share on a ring (figure, zone word and icon, one sentence). Retire the invented score from the hero
  (keep it only if it earns a place lower down, as the care score of the week, or drop it).
- *Suggested command:* `$impeccable shape`, then `$impeccable layout`.

**[P1] The same number is drawn three times, and every block looks the same.**
- *Why it matters:* the day's dose is a capsule meter, a ring with 83 % and a stat card; the week and headphones are two more identical
  ring cards. Nine same-weight white cards: the eye has nowhere to go, and a screen that is mostly "see more" rows adds a menu on top
  of the tab bar.
- *Fix:* one hero for today, one quiet secondary row for week and headphones, the day's timeline, the last alert. Merge the "see more"
  rows into the blocks they belong to (record where each one went).
- *Suggested command:* `$impeccable distill`.

**[P1] Large text breaks Home.**
- *Why it matters:* at font 2.0 the eyebrow reads "GALAXY WATCH7 · LI…", the link chip "Galaxy Watch7 · lin…" (ellipsised, so the state word
  is lost), the hero label "HEARING CARE SCORE" wraps to two lines. Truncating the one word that says whether the watch is linked is
  a status failure.
- *Fix:* the state word never truncates (wrap or move below); no ellipsis on status text; cap the display figure and let words grow
  (the watch rule, 1.3 and 1.5 times).
- *Suggested command:* `$impeccable adapt`, `$impeccable typeset`.

**[P2] Numerals and eyebrows come from a different system than the watch.**
- *Why it matters:* JetBrains Mono with `tnum` draws the dotted zero ("0%", "10", "dBA") that the watch dropped on purpose; uppercase
  letter-spaced eyebrows sit over most blocks (the watch rule is "no uppercase labels above things", also in STYLE.md). Reading the two
  devices one after the other, the numbers look like different instruments.
- *Fix:* Plus Jakarta Sans in fixed-width digit cells (reuse the watch `Numerals.kt`), sentence-case headings.
- *Suggested command:* `$impeccable typeset`.

**[P2] Charts break the data-visualisation rules the product is otherwise careful about.**
- *Why it matters:* guide lines are dashed (the 85 dBA and 100 % lines), bars fill their slots (about 37 dp wide on the week view)
  with no surface gap, there is a legend of four badges under a single-series chart, the hourly chart has no value label for the
  loudest hour, and there is no way to read a bar's value except a spoken description.
- *Fix:* thin capped columns with a 2 px surface gap and 4 px rounded tips, hairline solid guides labelled at the line's end, the loudest
  hour or day labelled directly, tap a bar to read it, zone as colour AND position (a zone band behind the bars).
- *Suggested command:* `$impeccable layout` with the dataviz rules.

**[P2] Tablets and foldables get a stretched phone.**
- *Why it matters:* at 1280 dp the Home column is 720 dp wide with 500 dp of empty canvas on the left, the rail is separate from the
  content, and the link chip floats in the middle. Nothing uses the extra width (no side-by-side hero and timeline).
- *Fix:* two panes from the medium width class: the verdict and the day on the left, the week and the last alerts on the right.
- *Suggested command:* `$impeccable adapt`.

## Persona red flags

**Casey (distracted mobile user)**: opens the app in a queue. The first screen says "A busy afternoon for your ears" and a score of
84 beside a meter whose fill reads as "nearly full" in a calm purple; the actual 83 % LOUD is below the fold and the primary
action ("Sync now") is a pull gesture. One-handed, the five tabs are fine (bottom bar), but the three cards of "see more" are at the very
bottom.

**Sam (accessibility-dependent user)**: at font 2.0 the status text is ellipsised ("lin…"), the hero label wraps into the figure's
column and the eyebrows are real uppercase strings (`.uppercase()`), not a text style, so they cannot be switched off for anyone. The charts have good descriptions; the ring cards read as one item each, which is right.

**Jordan (first-timer, new to dosimeters)**: "dose", "allowance", "budget", "care score" are four names for related things; the
care score is explained ("not a hearing test") but is the largest number on the screen.

## Minor observations

- The link chip is centred while everything else is left-aligned; on the tablet it floats alone.
- Hero shadow in light theme is 6 dp: the single heaviest object on a page that is otherwise flat tonal cards.
- "Updated 4 min ago" appears under each of five blocks on Home, always the same value.
- Setup is seven steps; fine, but the progress bar and the uppercase "STEP 1 OF 7" duplicate each other.

## Questions to consider

- What if Home were only the ring, one sentence and the day's strip, and the rest were one scroll away?
- Should the week be a sentence ("Better than last week") before it is a chart?
- Does Tono need to be the headline, or the caption under the figure?
