# Voxara icon concepts

All three are drawn from scratch as plain vector strokes, with no icon packs, fonts or traced artwork.
Geometry, layers and the comparison sheet come from `../tools/concepts.py` (rendered with resvg; see
`../tools/render.js`). `compare.png` shows each concept in the four adaptive masks at 192 and 48 px on
dark and light wallpapers, as a themed (monochrome) icon, in the Wear OS app list at 1:1 px, as the
watch splash, and as the 24 dp notification silhouette (1×, 3×, 8× zoom).

Files per concept: `concept-x.svg` (full icon), `-background.svg` / `-foreground.svg` (adaptive
layers, 108 canvas, mark inside r = 24 of the 33 safe radius), `-monochrome.svg` (themed layer),
`-notification.svg` (24 dp white silhouette).

Shared choices: dark `#090B10` → `#1A1F33` radial background, a soft accent halo, mark in a
`#C4B5FD → #8B5CF6 → #6366F1` gradient, glow faked with a wider 10 % stroke (no blur, as on the
watch). Everything is strokes with round caps, so each concept converts 1:1 to a VectorDrawable.

## A: Gauge V
The watch's 270° dose arc, open at the bottom, with a **V** whose point drops through the opening
like a gauge needle. It reads as "Voxara" and "measured level" at the same time and reuses the
app's most recognisable visual (the bezel arc).
*Not generic:* it is a monogram built from the product's own gauge, not an ear or a pictogram.
*Resemblance risk:* any "V in a circle" can recall the vegan Ⓥ or the VW roundel. Here the ring is
open, the V never touches it, and its point leaves the ring through the gap, which neither of those
marks does. At 24 px the gap is still visible.
*Small sizes:* the strongest of the three. Two strokes, wide gaps, a clear silhouette at 24 px.

## B: Quiet wave
A loud sound wave meets a curved guard and leaves it as a single calm point. The icon is the app's
promise (loud in, safe out) told left to right.
*Not generic:* no ear, no bars, no rings. Attenuation is shown as a story, which no hearing app
listed in the brief uses.
*Resemblance risk:* low for logos. The weak point is that at small sizes it can read like the
characters "∿(·" (an emoticon or a code glyph) rather than as a symbol.
*Small sizes:* legible at 48 px; at 24 px it holds, but it is the widest and least compact mark.

## C: Cochlea
Sound rings wound into the cochlea (the inner-ear organ that loud noise damages), ending in a short
base where sound enters. It is anatomically about hearing *health* without being a clip-art ear.
*Not generic:* nobody in the hearing/noise space uses the cochlea; Apple Noise, Sound Amplifier and
Samsung Health use ears, bars or hearts.
*Resemblance risk:* spirals are common. At 48 px it can read as "@" or as a snail shell, and it is
a distant relative of the Debian swirl (which is thin, red and asymmetric).
*Small sizes:* 2¼ turns with 11-unit turn spacing so the turns stay separate at 24 px; it does not
contain the dose arc idea.

## Recommendation
**A (Gauge V)**: the most recognisable at 24–48 px, ties straight into the 270° bezel arc used all
over the watch UI, and carries the name. B is the most original story but reads weakest as a
silhouette. C is the most "medical" but the spiral is the most likely to be read as something else.
