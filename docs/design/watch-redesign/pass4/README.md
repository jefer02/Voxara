# Watch redesign, pass 4: six pages to three

Evidence for pass 4 (see `PROGRESS.md`, "Watch redesign — pass 4"). Everything here was captured from the debug build
on the **real Galaxy Watch 7 (SM_L300, 432×432 px, 203 dp, Spanish)** and the `Wear_OS_XL_Round` emulator (480×480, 240 dp),
plus JVM renders (Robolectric + Roborazzi) at 192 / 203 / 216 / 227 / 240 dp × font 1.0 / 1.3 / 2.0.

| Folder | What |
|---|---|
| `before/watch`, `before/emulator` | The six pages of pass 3 (Overview, Now, Today, Week, Headphones, More) and headphones mode |
| `after/watch`, `after/emulator` | Now, Now in headphones mode (playing, and nothing playing), Dose on Today, Dose on Week, More (at rest and scrolled to its end) |
| `compare/watch_before_after.png`, `compare/emulator_before_after.png` | Before (top row) and after (bottom row) |
| `compare/watch_morph_strip.png`, `watch_morph_back_strip.png` | Frame strip of the Today to Week morph (and back) on the watch, driven by a real rotary event |
| `compare/emulator_morph_strip.png`, `emulator_morph.mp4` | The same on the emulator |
| `compare/morph_strip_jvm.png`, `morph_fade_strip_jvm.png` | Deterministic strips on a manual clock: the morph, and the reduced-motion cross-fade (`MotionFramesTest`) |
| `jvm/` | Renders at the sizes above, English and Spanish, large fonts, and the pages below their heroes |

The watch's week ring shows one big segment: that watch has one day of history. The morph strips with seven days are the JVM ones.

## The new structure

| Page | Says | Picture on the ring | Gap under the centre | Below the fold |
|---|---|---|---|---|
| **Now** (Ahora) | How loud is it, and is today fine | dBA scale, four zones that light up, marker | Tono's tag (2 lines, 1 line, or first detail) over a short dose line; in headphones mode, the chip | Tono's tag if it had no room, the full dose line, headphones quick switch, surroundings, situation mode, Concert note; in headphones mode also outside level, status, range, time left, why |
| **Dose** (Dosis) | How much of the allowance is used, today or this week | 24 hourly bars **morph** into seven day segments | The **Today \| Week** switch | Today: equivalent level, hourly capsules, coverage, how the dose works. Week: where the share comes from, daily bars, pace, streak, score, Tono's summary, headphones group |
| **More** (Más) | Tools | none (a list) | none | Ask, Mode, Settings, disclaimer |

## Where every piece of information lives now

| Before | Page | Now |
|---|---|---|
| **Overview** · dose ring with 50 / 80 / 100 % ticks | Overview | Dose / Today: the dose as the rolling figure with its zone word and icon, on the 24-hour ring. Now: "46% of today" under the zone. **The tick marks themselves are gone**; the thresholds still drive the zone tag and the alerts |
| Tono's line (tap = Ask, sparkle = cloud AI) | Overview | Now: the tag under the zone (same tap, same sparkle) |
| Live level, zone tag or resume pill | Overview | Now: the big figure, zone tag, resume pill |
| "4% of today" | Overview | Now: the foot |
| Headphones mode: arcs, stereo waves, moon, curved label, chip, quick-switch card, outside line | Overview | Now: unchanged, the scale is what opens into the arcs |
| **Now** · dBA scale, figure, zone, "Uncalibrated estimate" | Now | Now (kept) |
| "1h 30m left · 46% today" / "Not adding to dose · 46% today" | Now (foot) | Now: short on the hero ("46% of today" / "Not adding · 46% today"), the full sentence is the first detail |
| Surroundings, situation chip, bench note, Concert note | Now (details) | Now (details) |
| **Today** · dose %, caption, zone, 24 bars, "this hour" marker | Today | Dose / Today (the default view) |
| "Same as 54 dBA for 8 hours" | Today (details) | Dose / Today, first detail |
| Hourly capsules, measured time, NIOSH note, empty state | Today (details) | Dose / Today (details) |
| **Week** · worst weekly %, caption, zone, seven segments, each day spoken | Week | Dose / Week |
| "Around you 10%" and "Headphones 3%" | Week (details) | Dose / Week, first detail: "Around you 10% · Headphones 3%" |
| Daily bars, pace, streak, score, Tono's summary | Week (details) | Dose / Week (details) |
| **Headphones** · estimated level (≈), zone, "estimated" | Headphones | Now in headphones mode (the two arcs, ≈ figure) |
| Weekly listening share (when nothing plays) | Headphones | Now in headphones mode (the figure, "of your weekly listening limit") and Dose / Week ("Headphones 3%") |
| Indigo scale with the confidence band | Headphones | Now in headphones mode: the arcs. **The drawn band is gone**; the range is the line "Estimated · low confidence · 78–90 dBA" in the details |
| Connected / listening / not connected, time left, "Why estimated?" | Headphones | Now in headphones mode (details); with the mode off, Dose / Week, headphones group |
| Seven days of listening bars and their note, "no headphones" empty state, "your phone counts too" note | Headphones | Dose / Week, headphones group |
| "Allow Bluetooth details" and its note | Headphones | Mode screen, headphones section (only while the permission is missing and headphones are connected or detected) |
| Page names (Resumen, Ahora, Hoy, Semana, Auriculares) | all | Not drawn any more. Spoken: "Page 1 of 3, Now". Hoy and Semana are the labels of the switch |
| More · Ask, Mode, Settings, disclaimer | More | Same, with the bottom fixed |

Old routes still work: `home` and `now` open Now, `today` and `week` open Dose on that view, `headphones` opens Dose on Week
(its share lives there), `menu` opens More. Links: `voxara://watch/<now|today|week|headphones|more>`, and the extra `voxara.page`.
The dose tile and complication open Dose / Today; the listening tile and complication open Dose / Week.
Before this pass none of them named a page (they all opened Overview).

## Distill notes (impeccable)

The plan was confirmed with these changes, each for a reason in the renders:

1. **No visible page name.** With three pages the dots say where you are, and each page says what it is: the figure with `dBA` and the zone word (Now), the switch (Dose), the title (More). The name slot is what lets Now carry Tono's tag and the dose line. TalkBack keeps "Page n of 3, name".
2. **Tono's tag lives in the ring's gap, and it degrades.** On the watch the gap under the centre is about 49 dp tall (203 dp, with the "Uncalibrated estimate" note); a 2-line tag plus a dose line needs 58. So `PageStage` hands the foot its room and Now shows 2 lines, 1 line, or moves the tag to the first detail. Calibrated watches (no note) and 227 dp or larger get 2 lines.
3. **The last line is short.** The first try put the full dose line last; at 91 % of the height a 203 dp circle is 115 dp wide and the glass cut the text ("46% todav"). The hero shows "46% of today"; the tag, which sits higher where the gap is wider, may take 76 % of the width.
4. **The weekly breakdown is the first detail of Week, not a hero foot line.** The switch is 48 dp; with the figure, caption and zone tag it uses all 49 dp the gap has. A foot line would have pushed the switch out of the gap. (The breakdown is in the spoken description of the hero too.)
5. **Crown at the top flips, anywhere else scrolls.** The page rests at its top most of the time, so the crown is free there; once scrolled, or past Week, it scrolls the details.
6. **`headphones` links open Dose / Week**, because the listening tile shows the weekly share and that is where it lives now.

Critique notes (no numeric score; the earlier passes' scores were self-assessed and a new one would be too):

- *Hierarchy*: each page has one figure and one question; Hick's cost drops from six destinations to three. Now carries five things (figure, zone, note, Tono's tag, dose): it is the densest page and the one that degrades by room.
- *Discoverability*: the switch is visible; the crown is not, and the large-font arrangement hides the switch below the fold (the crown and the detail row remain).
- *Consistency*: the gap's one control is always 48 dp and always in the same place (chip, switch).
- *Known rough edges*: with one day of history the Week ring is one segment (unchanged from pass 3); digit cells roll independently, so 4 to 13 flashes "43" for a frame.

## By hand on the watch

- The crown on Dose: one notch clockwise flips to Week, back flips to Today. The debug broadcast used here (`--ef crown 3`) delivers a real `SOURCE_ROTARY_ENCODER` scroll event, but a physical or touch bezel may send different step sizes: if one notch flips twice or not at all, `ROTARY_FLIP_PX` in `DoseScreen.kt` is the number to change. Check scrolling the week details with the crown once on Week, and that the crown still scrolls Settings and the More page.
- Swipe back from Now exits the app (it did in the JVM test with the nav host; the One UI Watch gesture needs your hand).
- Tap the tag (Ask) and the switch with the strap on; the tag's visible height is 28 to 44 dp, widened to 48 dp by the platform's touch slop.
- The tile and complication taps (dose to Today, listening to Week): add them to a watch face and tap.
- TalkBack: "Page 2 of 3, Dose", the switch read as tabs, the crown with TalkBack on.
- The Tono tag with real, long sentences in Spanish and English at font 1.0 and 1.3.
- More: scroll to the end, and look at the rest state under the dots.
