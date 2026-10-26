# Pass 3 critique (self-assessment, impeccable: layout, adapt native, animate, delight, polish, audit native)

Evidence: the real Galaxy Watch 7 (203 dp), the XL round emulator (240 dp) and JVM renders at 192 / 216 / 227 / 240 dp,
font 1.0 / 1.3 / 2.0, English and Spanish. A self-assessment, not an outside review.

## What was wrong (from the screenshots)
- The ring sat 11 % in from the glass with a wide black margin, and every page drew it a little differently (outer edge 57 to
  78 px from the glass on the real watch), so Overview looked smaller than Now.
- Sizes were fixed dp and sp: on the 240 dp display the figure, words and waves were small for the space.
- Tono's line took three lines in a 118 dp column and ended in an ellipsis; at font 2.0 it took the whole hero.
- `tnum` turned "1" into a slab-footed glyph ("11", "13" looked like another font).
- Week: grey segments, one purple pill and a lone white dot; Headphones: "de la semana" with no subject.
- The system clock overlapped the ring once the ring moved to the edge.

## What changed, and how it reads now
- One rim, one stroke, fractions of the display: the ring's outer edge is 18 to 22 px from the glass on every page of the real
  watch; the waves fill the inside; figures and words scale 0.9 to 1.15× with the display.
- The ring's gap is 110° (it was 90°) so the foot lines clear the ring's ends at the edge; the clock steps aside on the hero pages.
- Tono's line is two lines at most, stepped down, then shortened whole; held at 1.3× the font.
- Week reads: seven separated segments, a stub for a quiet measured day, dots for a day with no data, today underlined; every day
  is also spoken.
- Headphones mode: the signature moment is staged (ring, waves, figure, moon, label, chip), about 900 ms, reads as one gesture
  on the watch and never competes with an alert.

## Scores (out of 40 / 20; pass 2: 34 / 16)
| | Pass 3 | Why |
|---|---|---|
| Critique (Nielsen) | 36 / 40 | Fills the screen on both devices, one consistent ring, status and mode visible; the Week ring still needs a learning moment (dots mean "no data"), and the chip's two lines are dense at 203 dp |
| Audit native | 17 / 20 | 48 dp targets, fonts 1.0 / 1.3 / 2.0 without clipping, contrast tests green, TalkBack text for every day and the mode change, reduced motion and ambient handled; not run with TalkBack on the watch |

## Open points (honest)
- A 3.4 % janky-frame rate over five transitions on the watch, with a 125 ms worst frame at the start of each: the second centre is
  composed the moment it fades in. Worth a look if it is visible on the wrist (pre-composing it would cost battery at rest).
- The moon's number stays at 13 sp at any font (it must fit its disc); the same figure is in the details and in the spoken text.
- Without the optional Bluetooth permission a Bluetooth speaker looks like headphones to the layer.
