# Phone redesign: evidence and decisions

The phone app was redesigned to be the watch's other half ("Orbit, enlarged"), with the Ledger concept's charts in Trends. This folder is the record.

| What | Where |
|---|---|
| The surface brief (mode Operate, the one question, scenes, constraints) | `SURFACE_BRIEF.md` |
| Before: critique 26/40, audit 13/20, 280 renders (local only, ignored by git) | `before/` |
| After: critique 35/40, audit 18/20, 305 renders (local only, ignored by git) | `after/` |
| Style and design documents | `docs/design/STYLE.md` (§2, §3, §4, §5e, §6, §8), `DESIGN.md` (Voxara Phone) |

Regenerate the renders (opt-in, they are slow): `VOXARA_SHOTS_DIR=<dir> ./gradlew :phone:testDebugUnitTest --tests '*PhoneRenderMatrixTest*'` with `JAVA_HOME` set to Android Studio's `jbr`.
Every render sits inside the real navigation (four tabs), at phone / foldable / tablet widths, font scale 1.0 / 1.3 / 2.0, light and dark; the full-length screens, the states of Home, the Watch screen and the headphones, and the four accents are included.

## Decisions (from the checkpoint)

1. Concept A (the ring) for Home; the Ledger's charts for Trends (week columns, heat calendar, rolling-week line) in Orbit's language: zone colours, rounded caps at the data end, the watch's stroke weights, the same motion. The cumulative "where the day went" line moved into Today, the screen the ring opens.
2. Four tabs: Home, Trends, Watch, Settings. Tono opens from Home's sentence and from Trends' "Ask Tono" chip (one screen, one route).
3. The Hearing Care Score is a supporting line in Trends.
4. The range filter is a Material 3 segmented button (a filter, not navigation).

## Information map: where everything went

Every screen and route still exists. Nothing was deleted; what changed is how many places you have to look, and **every destination is at most two taps from Home**.

| Destination | Taps from Home | How |
|---|---|---|
| Today (detail) | 1 | the ring (shared element) or "Today, hour by hour" |
| Tono | 1 | Tono's sentence under the ring (or Trends → "Ask Tono") |
| Alerts | 1 | the last-alert row |
| Headphones | 1 | the headphones tile |
| Trends, Watch, Settings | 1 | the tabs |
| How it works | 2 | Settings → About |
| Run setup again, CSV export, delete all data, version | 2 | Settings |

| Before | After |
|---|---|
| Home: "Hearing care score" hero with capsule meter | Home: the ring. The score is a supporting line in Trends |
| Home: link chip and the same line as the eyebrow | One status line: the watch and when it last reported; a problem replaces it with the state word and its action; everything else about the link is on the Watch tab |
| Home: Tono headline in the hero | The sentence under the ring (opens Tono) |
| Home: uncalibrated banner | One compact line with its action under the sentence, for as long as the watch is uncalibrated; never hidden |
| Home: stale banner, battery guide | One banner slot above the ring, only when relevant |
| Home: Today / 7-day budget / Headphones stat cards (three rings) | The ring; two quiet tiles (last 7 days, headphones), each opens its detail |
| Home: "Today, minute by minute" strip card | The hour columns on Home; the minute strip moved into Today |
| Home: last-alert card and "see today / alerts / headphones" rows | One last-alert row (opens Alerts); the three "see" rows are gone: the ring, the row and the tile are the links |
| Today | Opened from the ring; keeps the hours, average, loudest minute, measured time; gains "where the day went", the minute strip and today's alerts |
| Alerts screen | Unchanged destination |
| Headphones: sharing switch and headphone type | Settings → Headphones (the Headphones screen has a first row that opens it); the long explanation moved to How it works |
| Trends: day / week / month with five cards | One segmented filter, one hero and one chart per range, the rolling weekly allowance, the headphones' share (one line), the care score (one line), "Ask Tono" |
| Watch: device card, checklist, battery tips, calibration how-to, troubleshooting (5 rows), other watches | Device card and checklist stay; battery tips and calibration open in their checklist rows; troubleshooting is one expandable row |
| Settings: ten groups | Six: Watch and alerts, Headphones, Tono and privacy, Appearance (with language), Data, About |
| Tono tab | Same screen, opened from Home and Trends |
| Glance widget | The watch's ring and digits, the zone as a word, "As of" the time |

## Four names for four numbers

`HearingFigures` (phone/…/model/Figures.kt) is the one source for Home, Today and Trends: **today** (share of the daily allowance), **surroundings, last 7 days** and **headphones, last 7 days** (each a share of the weekly allowance) and **all sound, last 7 days** (those two added, compared with the 7 days before). The seven days are always the rolling last seven, never a calendar week. The inconsistencies found in the concept renders were: a fixture problem (the status was typed by hand and disagreed with the ledger; the clock was fixed to a different weekday than the labels; the Tono copy was hand-written), a label problem (Home's week was surroundings only while Trends' was surroundings plus headphones, both called "this week") and one real-logic risk (Home read the watch's status, Trends read the ledger, so a lagging sync could show two figures for today). The tests are in `FiguresConsistencyTest`.

## Motion

| Motion | Reduced motion |
|---|---|
| The Home ring grows into Today (shared element) | plain fade |
| The ring's arc draws in, then eases to a new value | the final frame |
| The figure rolls up from 0 on Home and digit by digit on a change | plain digits |
| Charts draw in once (columns grow in turn, lines draw along their path) | the final frame |
| Pull to refresh: three sine lines in the accent that rise with the pull and keep moving while it syncs | still lines, amplitude follows the pull |
| Theme and accent changes crossfade every colour (360 ms) | instant |
| Tabs cross-fade with a 4 % scale; detail screens slide in 10 % and fade | 120 ms fade |
| Cards, tiles and rows give 97 % press feedback | none |
| Expandable rows open in place, the chevron turns | instant |
| The sync button turns while syncing | still |

Delight is deliberately small: the one signature is the ring-to-Today shared element and the figure counting up; no confetti, no sounds, nothing that is not information.

## Tests added or changed for the redesign

`FiguresConsistencyTest` (Home, Today and Trends from one state; the week's arithmetic; no daily alert above its day's dose, in the fixtures and in the real `AlertEngine`; dates and calendar columns; Tono's day part follows the data), `FontScaleLayoutTest` (16 screens at font 1.3 and 2.0: no broken word, no cut text, no overlap), `ChartColorsTest` (every accent × theme: marks ≥ 3:1, text ≥ 4.5:1, accent / amber / crimson apart in normal vision and for three kinds of colour-blindness), `WidgetArtRenderTest`, `AlertHistoryWriterTest` (an acknowledgement that lands before its row is written is not lost). Thresholds were not touched; the one colour that failed (Emerald, dark, against crimson for deuteranopia) was changed.

## Known gaps

- Chart points cannot be explored by touch (values are printed or spoken).
- Today, Alerts, Settings and Watch are one centred column on a tablet.
- Settings keeps its grouped-list look and its length.
- Not yet seen on a real phone or a Galaxy Watch 7: see the final report's list.
