# Watch redesign: two concepts for Home and Now

Static frames rendered with the Roborazzi harness from throw-away test composables (deleted after the choice; nothing was wired into the app).
Files: `concept_<A|B>_<screen>_<216dp_x1.0 | 192dp_x1.3>.png`, contact sheets `sheet_A_orbit_216.png` and `sheet_B_dial_216.png`.
Sample data: 88 dBA (Loud), 46 % of today's dose, "Loud café. Week on track." as Tono's line.

## Shared across both concepts
- Numerals are drawn in Plus Jakarta Sans with no dotted zero (see "Numerals" below). The unit reads `dBA`.
- At most one small label per screen; the zone is always word + icon.
- Everything sits inside the 270° bezel arc, which is centred on the display and leaves the TimeText a clear band at the top.
- Wave rings + halo stay behind the number, driven by the live level.

## Concept A: "Orbit" (HorizontalPager)
**Structure.** Each destination is a full-screen round hero; swipe sideways between Now, Today, Week and Headphones, with the page dots in the gap of the arc. Home is the first page and combines the three things you asked for in one viewport: daily dose on the 270° arc, live dBA number, zone badge, and Tono's one-line status under it. Ask and Settings live on a scrollable page after the pager (edge button), so the glance pages stay free of lists.
**Strength.** Maximum glanceability: nothing to scroll, every page is a single hero, nothing is a rectangle in a circle.
**Risk.** Tono's one-liner is truncated to ~25 characters at 12 sp (ellipsis in the frame); needs a short-line prompt or a tap-to-expand. Conflicts with `SwipeDismissableNavHost` edge swipe (needs an edge-aware pager or the back gesture from the first page only). At 192 dp × 1.3 the number drops to the Display size and the scene label disappears.
**Animation.**
- Opening a page: the arc sweeps from 0 to its value (about 600 ms, decelerate), the zone colour crossfades (400 ms).
- The dBA number rolls digit by digit (200 to 300 ms) instead of jumping.
- Between pages: the hero slides with a slight scale/fade parallax, the page dot pill stretches to the new position.
- Wave rings widen and speed up with level, settle to near-still below about 45 dBA; static with reduced motion; none in ambient.
- Crossing into Loud / Dangerous: one brief ring pulse (not repeating).

## Concept B: "Dial + list" (TransformingLazyColumn)
**Structure.** Home is a compact dial (arc + number + zone word) with Tono's status under it; a swipe up reveals compact full-pill destinations (Now, Today, Week, Headphones, Settings) on a `TransformingLazyColumn`, with `Ask Tono` on the edge button at the end of the list. Now has the same dial larger, the scene as its one label, and the dose as one line.
**Strength.** Native Wear pattern (scrolling list, edge button, scroll indicator); no new gesture conflicts; status text can wrap to two lines.
**Risk.** The dial is smaller than in A (112 to 124 dp vs about 170 dp) and Ask is only visible at the end of the list; at 192 dp × 1.3 the status line pushes the first chip into view, so the hero's one-viewport promise depends on text length.
**Animation.**
- Opening: the dial's arc sweeps in (about 600 ms) and the number rolls up from the previous value.
- List: items scale and fade toward the top and bottom edges as they scroll (TransformingLazyColumn spec); the edge button grows in when the list ends.
- Chips give a short press scale (about 100 ms); returning from a destination restores the scroll position.
- Waves behave as in A.

## Numerals (problem 5): what was verified
Rendered `numeral_probe.png` with JetBrains Mono under `tnum`, `zero`, `ss01`, `ss02`, `cv01` and `cv12`: the zero keeps its dot in every case, so it cannot be turned off with OpenType settings in this bundled variable font. Plus Jakarta Sans draws a plain zero. I could not confirm `tnum` support in Jakarta from renders alone (no font tooling installed), so the build will lay out numbers in fixed-width digit cells (one cell per digit, sized to the widest digit): tabular by construction, and the same cells make the rolling-digit animation possible. JetBrains Mono stops being used for numerals on the watch.
