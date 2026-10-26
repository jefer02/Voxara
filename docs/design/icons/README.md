# Voxara icons

**Mark: "Gauge V"** (concept A of `concepts/`): the watch's 270° dose arc, open at the bottom, and a V
whose point drops through the opening like a gauge needle. Drawn from scratch as round-cap strokes,
so there are no third-party assets and no licence to track.

| Folder | What |
|---|---|
| `concepts/` | The three concepts, `compare.png`, and the rationale (`README.md`) |
| `final/` | Source SVGs (`voxara-icon.svg` = full icon; launcher layers; watch variants; `notification.svg`) and `preview.png` |
| `store/` | Play Store: `play-icon-512.png` (listing icon), `play-icon-wear-512.png` (watch art, only if a separate Wear listing ever exists), `feature-graphic-1024x500.png` |
| `tools/` | `final.py` (the generator), `concepts.py`, `render.js` (resvg) |

## Regenerate

```sh
npm install --prefix <some-dir> @resvg/resvg-js          # once, outside the repo
NODE_PATH=<some-dir>/node_modules python docs/design/icons/tools/final.py
```

`final.py` writes, from one geometry definition:
- source SVGs in `final/`;
- the Android VectorDrawables in both apps (`ic_launcher_background`, `ic_launcher_foreground`,
  `ic_launcher_monochrome`, `ic_voxara_status`) and the adaptive-icon XML (`mipmap-anydpi` in both apps; on the
  phone minSdk 28 makes a `-v26` qualifier redundant);
- the Wear tile previews (`app/…/drawable-nodpi` and `drawable-round-nodpi`, `tile_preview_dose.png`,
  `tile_preview_listening.png`);
- the store PNGs and `final/preview.png`. The preview is rendered from the VectorDrawable XML that
  ships, parsed back into SVG, not from the source SVGs.

Edit the generator, not the generated XML. Geometry knobs: `RING_R`, `RING_W`, `V_PTS`, `OPTICAL_DY`,
and the per-target placements `PHONE`, `WATCH` (bigger and bolder for the small round screen) and
`NOTIF`.

## Rules the set follows

- Launcher layers are 108 dp. The mark stays inside r ≈ 24 dp (watch r ≈ 26), well inside the 66 dp
  safe zone, so every OEM mask (circle, squircle, rounded square, teardrop) keeps a margin.
- The foreground layer doubles as the splash icon: the mark sits inside the splash's visible 2/3
  circle (phone 288 → 192 dp; watch core-splashscreen 90 → 60 dp).
- Monochrome layer = the same strokes in white (only alpha is used by themed icons).
- Notification / complication icon: 24 dp, white, no gradients, 2.4 dp strokes, ≥ 1.2 dp gaps.
- Glow is the background's radial halo, never a stroke on the mark (a wide translucent stroke reads
  as a hard ghost outline at 192–512 px).
