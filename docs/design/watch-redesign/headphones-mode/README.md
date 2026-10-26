# Headphones mode: the transition

The listening layer turning on and off on the Overview hero. Design and rules: `DESIGN.md` (Headphones layer, Motion),
`docs/design/STYLE.md` §5c, `PROGRESS.md` ("Headphones mode"). Code: `ui/design/LayerTransition.kt` (stages),
`ui/screens/HomeScreen.kt` (centres, chip, label), `ui/design/Orbit.kt` (`OverviewRing`, `Moon`).

## Real watch (Galaxy Watch 7, SM_L300, 203 dp, debug build, `adb screenrecord`, ~41 fps)

| File | What |
|---|---|
| `watch_enter.gif`, `watch_enter.mp4` | Off to on (listening estimate 82 dBA, outside 58 dBA) |
| `watch_leave.gif`, `watch_leave.mp4` | On to off |
| `strip_watch_enter_wide.png` | 16 frames of the enter, with milliseconds (the recorder only emits frames when the picture changes, so the spacing is uneven) |
| `strip_watch_leave.png` | 16 frames of the leave |

The enter, in order: the 250° ring opens at the top into two indigo arcs (spring) · the wave rings go stereo · the old centre
(Tono's line, outside level, zone) fades out and the listening centre (≈ level, "estimated", zone) fades in with the digits
rolling · the outside level flies out as the moon and settles in the top gap · the curved label "Modo auriculares" appears
at the bottom while the old foot and page name fade under it, then the chip "Auriculares / Manual" takes the place.

## JVM frame strips (deterministic, manual frame clock, 216 dp x1.0)

`strip_jvm_enter.png` and `strip_jvm_leave.png` are laid out from `MotionFramesTest.headphonesModeEnters` /
`headphonesModeLeaves` (frames at 0, 90, 180, 270, 360, 450, 540, 630, 720 and 900 ms after the trigger). They show the staging
without any device jank.

## Notes
- The emulator (software GPU) cannot show the timing: its transition janks. The real-watch files are the reference.
- Frame stats on the watch over five enter + leave cycles: 1370 frames, 3.4 % janky, 99th percentile 125 ms (a hitch at the
  start of each transition); idle on the Overview 0.39 % janky, idle in headphones mode 0.82 %.
- Reduced motion cross-fades only; ambient shows a still two-arc outline with a small headphones icon.
