# Checkpoint B: two concepts for the phone

Home and Trends, purple accent, light and dark, 360 × 800 dp. Both answer the one question from `../SURFACE_BRIEF.md` ("How is my
hearing today?") in the first viewport, both use the watch's numerals (Plus Jakarta Sans in fixed-width digit cells, plain zero), both
drop the uppercase eyebrows, the dashed guides, the invented "care score" as the hero and the nine-card stack. They differ in what the page
IS.

Built with the real theme, the real data models (`PhoneUiState`, `History`, `weekCompare`, `rollingWeeklyPercent`) and the new shared
numerals (`shared-ui/.../DigitCells.kt`); the concept code lived in `phone/src/test/.../ui/concepts/` (test sources only, never shipped) and was **deleted once the build was done** (see `../README.md`); the images and this comparison stay as the record.
Fake data (`PhoneFixtures`): the same afternoon that reached LOUD, and a calm variant with the same history. English strings only; the
build uses resources in EN and ES.

| File | What |
|---|---|
| `compare_home_loud.png` | Home, loud state: A light, A dark, B light, B dark |
| `compare_home_calm.png` | Home, calm state (34 %), same order |
| `compare_trends_week.png` | Trends, Week range, same order |
| `compare_trends_month.png` | Trends, Month range, same order |
| `home_{a,b}_{loud,calm}_{light,dark}.png`, `*_full.png` | the individual screens (full length for the loud state) |
| `trends_{a,b}_{week,month}_{light,dark}.png` | the individual Trends screens |
| `home_{a,b}_loud_light_x2.0.png` | font 2.0 probes (see "Known gaps") |

## Concept A: "Orbit, enlarged"

The watch's picture, made phone-sized. **Home is one ring**: today's share of the allowance as a very large figure inside the 250° arc
(same 110° gap at the bottom as the watch), the zone as a word with its icon, and Tono's sentence in the gap under the ring. Under it
two quiet tiles (this week, headphones) with a meter to the 100 % line, then the hours of the day as thin capped columns with the loudest
hour named. The surface is flat (no card shadows), the depth is light (a soft halo in the zone's colour).
**Trends** is the same ring with the week's seven days as segments and the week's share as the figure; under it "Day by day" is a list
of rows with a meter each (the table twin of the ring, and what a screen reader walks).

- *Strengths:* the closest kin to the watch (one glance, one picture, one word); the strongest motion story (the ring on Home is the
  ring that opens Today, a shared element; the arc draws in; the figure rolls); calm; easy to read at arm's length.
- *Risks:* the ring is a fixed square, so at large fonts and on small phones the text inside competes with the arc (see "Known gaps"); a
  ring of seven coloured segments is the weakest chart of the set (shares are hard to compare around a circle), which is why the list of
  rows sits right under it; Home shows the day's shape lower down than B.

## Concept B: "The Ledger"

Number first, and the day as a story. **Home is one tinted surface** whose colour is the zone (cream for LOUD, violet-tinted for OK; the
tint is the tier colour at 15 %): the figure, the zone word, Tono's sentence, and "Where the day went", a line that shows the dose building
up through the hours and ending at the figure, with the 100 % limit as one labelled hairline. Under it three plain rows with hairlines
(this week, headphones, last alert); no ring anywhere and no card shadows.
**Trends** leads with the week's figure and a sentence, then the daily dose as seven capped columns with every value on its cap (seven is
few enough to label all), the rolling week as a thin line, and, for the Month range, a **heat calendar** (each day a cell in the zone it
reached, the weekday columns aligned, the number on the cell, the four zones as a legend with icons).

- *Strengths:* the most information in the first screen without noise ("where did my allowance go today" is something the watch cannot
  show and the phone can); the charts follow the dataviz rules (thin capped marks, hairline solid guides, direct labels on the extremes,
  one idea per chart, zone as colour and position); denser and more editorial; the heat calendar is the natural month view.
- *Risks:* further from the watch (no ring), so the two devices look related by numerals and colour only; the tinted hero changes the
  page colour with the zone (intended, but it is a lot of colour when the day is LOUD); the cumulative line needs the day's minute ledger,
  so it is empty until the first sync after midnight and the hero must degrade gracefully (the figure stays).

## Side by side

| | A: Orbit, enlarged | B: The Ledger |
|---|---|---|
| Answers "how is my hearing today?" | A figure on a ring, a word, a sentence | A figure on a zone-tinted surface, a word, a sentence, and the line that explains it |
| First viewport (360 × 800, font 1.0) | ring, sentence, week and headphones tiles, top of the hour chart | title, hero with the build-up line, week, headphones and last alert rows |
| Family resemblance to the watch | strong (same ring, same gap) | medium (numerals, zone colours, no ring) |
| Information per screen | lighter; the hours chart is below the tiles | heavier; the day's story is in the hero |
| Best chart | hour columns with the loudest hour named | cumulative dose line, capped day columns, heat calendar |
| Weakest chart | the segmented week ring | none; the hero tint is the risk |
| Motion story | ring → Today shared element, arc draw-in, number roll | hero morphs into Today, line draw-in, column grow, number roll |
| Tablet / unfolded | ring left, week/hours/alerts right: a natural two-pane | hero left, week/month charts right |
| Large text (font 2.0) | the ring must be taller than wide or the figure must leave it | stacks cleanly (hero is a column) |
| Build effort | medium (ring, week ring, morph) | medium (hero surface, build-up line, calendar) |

**My recommendation: A for Home, B's charts for Trends** (the cumulative line can also live in Today). A is the version that makes the
phone feel like the watch's other half and gives the shared-element and number-roll motions something real to do. B's data graphics are
better than A's week ring and are exactly what dataviz asks for. If you want one concept as it is, pick A for the family, B for the
information; either is buildable across all screens.

## Decisions needed with your choice

1. **Concept**: A, B, or "A for Home and B-style charts in Trends" (recommended).
2. **Navigation**: today five tabs (Home, Trends, Tono, Watch, Settings). Fewer menus: **four tabs** (Home, Trends, Watch, Settings) with
   Tono opened from Home's sentence and from a "Ask Tono" chip in Trends (same screen, same route `tono`, back returns), or keep five. I
   recommend four (Material's bar takes 3–5; four leaves each label room at font 2.0). Tell me if Tono must stay in the bar.
3. **The "Hearing care score"** (84): out of the hero. Keep it as a supporting line in Trends ("Care score 84 · listening habits, not a
   hearing test") or drop it. I recommend keeping it there.

## Information map (what moved where; identical for both concepts unless noted)

Nothing is deleted. Every screen and route (`home`, `today`, `alerts`, `trends`, `tono`, `watch`, `headphones`, `settings`, `how`, setup)
stays reachable; what changes is how many places you have to look.

| Before | After |
|---|---|
| Home: "Hearing care score" hero with capsule meter | Home: the ring (A) or the zone surface (B). The score moves to Trends (a supporting line) |
| Home: link chip (and the same line as the eyebrow) | One status line at the top of Home ("Galaxy Watch7 · updated 4 min ago"); everything about the link is on the Watch tab; a problem state replaces the line with the state word and its action |
| Home: Tono headline in the hero | The sentence under the ring (A) or in the hero (B); a tap opens Tono |
| Home: Uncalibrated banner | One line under the sentence while uncalibrated (never hidden), with the action |
| Home: stale banner, battery guide | Same content, one banner slot above the figure only when relevant |
| Home: Today / 7-day budget / Headphones stat cards (three rings) | Today = the ring/hero. Week and headphones = two compact tiles (A) or rows (B); each opens its detail |
| Home: "Today, minute by minute" zone strip card | The hour columns (A) or the build-up line (B) on Home; the minute strip moves into Today (detail) |
| Home: Last alert card + "see today / alerts / headphones" rows | One "last alert" row (opens Alerts). The three "see" rows are gone: the ring, the row and the headphones tile are the links |
| Today screen | Opened from the ring / hero (shared element). Keeps hour chart, average, loudest minute, measured time; gains the minute strip and today's alerts (they were two screens' worth of repetition) |
| Alerts screen | Unchanged destination, opened from the last-alert row and from Today's alert section |
| Headphones screen | Destination stays (opened from the headphones tile); the sharing switch and headphone type move to Settings → Headphones; the long explanation moves to How it works; the live phone-headphones card shows on Home when the phone's earbuds are playing |
| Trends: day / week / month, five cards | One range switch, one chart per range, the week-over-week sentence, the rolling-week line, the headphones share as one line |
| Watch screen: device card, checklist, battery tips, calibration how-to, troubleshooting (5 rows), other watches | Device card and checklist stay; battery tips and calibration how-to fold into their checklist rows (tap to expand); troubleshooting becomes one expandable "Troubleshooting" row |
| Settings: ten groups (Mode, Headphones mode, Alerts, haptics, Headphones, Tono, Appearance, Language, Data, About) | Six: Watch (mode, headphones status, alerts, haptics), Headphones, Tono and privacy, Appearance (with language), Data, About |
| Tono tab | Screen unchanged; reached from Home / Trends (see decision 2) |

## Known gaps in these concepts (not in the build)

- **Font 2.0**: the `x2.0` probes show what is still to do: A's caption "of today's allowance" collides with the arc and the tiles break
  "Headphones" mid-word; B's hero stacks fine. In the build the figure is capped at 1.3×, the caption moves below the ring at large
  sizes, and the tiles switch to one column (the watch's rules). Not final.
- No motion here (renders are still frames, reduced motion on). The motion plan is in `../README.md` once a concept is chosen.
- The hour chart in the calm state shows the same history as the loud one (one fixture).
