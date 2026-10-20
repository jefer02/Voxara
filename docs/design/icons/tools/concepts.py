"""Voxara icon concepts: geometry, SVG layers and the comparison sheet.

Every concept is drawn once in a "design frame" centred on (0, 0) whose outer extent is a radius
of about 30 units, then placed:
  * launcher foreground: 108 x 108 adaptive canvas, centre (54, 54), scaled into the 66 dp safe zone
    (the visible mark stays inside r = 24, so even the tightest mask keeps a margin);
  * notification: 24 x 24 canvas, live area 20 dp (r = 10), white, no gradients.

Run:  python concepts.py   (needs Pillow, and node + @resvg/resvg-js on NODE_PATH; see render.js)
"""
import json
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "concepts"))
TMP = os.path.join(OUT, "_render")

BG_DARK = "#090B10"
CARD = "#131722"
ACCENT = "#8B5CF6"
ACCENT_INK = "#A78BFA"
SECONDARY = "#6366F1"


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pt(x, y):
    return f"{f(x)},{f(y)}"


def polar(r, deg, cx=0.0, cy=0.0):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


# ---------------------------------------------------------------------------------------------
# Concept A — "Gauge V": the 270° dose arc, open at the bottom; a sound wave folds into a V whose
# point drops through the opening like a gauge needle.
# ---------------------------------------------------------------------------------------------
def concept_a(w=1.0):
    R, sw = 25.5, 7.0 * w
    x0, y0 = polar(R, 135)
    x1, y1 = polar(R, 45)
    ring = f'M{pt(x0, y0)}A{f(R)},{f(R)} 0 1 1 {pt(x1, y1)}'
    # V: straight arms; the point drops through the arc's opening like a needle.
    v = 'M-12.5,-10L0,19L12.5,-10'
    return [("stroke", ring, sw), ("stroke", v, sw)]


# ---------------------------------------------------------------------------------------------
# Concept B — "Quiet wave": a loud wave meets a curved guard and leaves it as a calm line.
# ---------------------------------------------------------------------------------------------
def concept_b(w=1.0):
    sw = 6.6 * w
    # loud wave, one full period, amplitude ~12
    wave = 'M-29,1C-27.5,-16.5 -17,-16.5 -15.5,0S-4,16.5 -2.5,-1'
    # guard: arc of a circle centred to the right, convex towards the wave
    cx, r = 30.0, 24.0
    gx0, gy0 = polar(r, 230, cx)
    gx1, gy1 = polar(r, 130, cx)
    guard = f'M{pt(gx0, gy0)}A{f(r)},{f(r)} 0 0 0 {pt(gx1, gy1)}'
    return [("stroke", wave, sw), ("stroke", guard, sw), ("dot", (21.5, 0), sw * 0.6)]


# ---------------------------------------------------------------------------------------------
# Concept C — "Cochlea": sound rings wound into the inner ear (the organ noise damages); the outer
# turn is the 270° dose arc and ends in a level dot.
# ---------------------------------------------------------------------------------------------
def concept_c(w=1.0):
    sw = 6.4 * w
    s = 5.5                       # radius step per half turn -> 11 between turns
    ax, bx = 0.0, s
    r1, r2, r3, r4 = 4.8, 4.8 + s, 4.8 + 2 * s, 4.8 + 3 * s
    d = (f'M{pt(ax + r1, 0)}'
         f'A{f(r1)},{f(r1)} 0 0 0 {pt(ax - r1, 0)}'      # inner turn, over the top (centre A)
         f'A{f(r2)},{f(r2)} 0 0 0 {pt(bx + r2, 0)}'      # under (centre B)
         f'A{f(r3)},{f(r3)} 0 0 0 {pt(ax - r3, 0)}'      # over (centre A)
         f'A{f(r4)},{f(r4)} 0 0 0 {pt(bx, r4)}'          # last quarter, down to the base
         f'L{pt(bx + 9, r4)}')                          # the base, where sound enters
    return [("stroke", d, sw)]


CONCEPTS = [
    ("a", "Gauge V", concept_a),
    ("b", "Quiet wave", concept_b),
    ("c", "Cochlea", concept_c),
]


# ---------------------------------------------------------------------------------------------
# Layout helpers
# ---------------------------------------------------------------------------------------------
def bounds(shapes):
    """Rough bounds by rendering would be exact; here sample path points numerically via resvg is
    overkill, so every concept is drawn already centred and we only use a fixed fit radius."""
    return None


FIT = {"a": 1.0, "b": 0.95, "c": 1.05}   # per-concept scale tweaks, tuned by eye on the sheet
OFFSET = {"a": (0, 2.5), "b": (3.4, 0), "c": (0, -2.6)}


def place(shapes, cx, cy, k, paint, w_extra=1.0):
    """SVG elements for shapes placed at (cx, cy) with scale k. paint = stroke/fill value."""
    out = []
    for kind, d, sw in shapes:
        if kind == "stroke":
            out.append(f'<path d="{d}" fill="none" stroke="{paint}" stroke-width="{f(sw * w_extra)}" '
                       f'stroke-linecap="round" stroke-linejoin="round"/>')
        elif kind == "dot":
            x, y = d
            out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(sw * w_extra)}" fill="{paint}"/>')
    return f'<g transform="translate({f(cx)},{f(cy)}) scale({f(k)})">' + "".join(out) + "</g>"


def svg(w, h, body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
            f'<defs>{defs}</defs>{body}</svg>')


GRAD = (f'<linearGradient id="ink" x1="30" y1="28" x2="78" y2="82" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="#C4B5FD"/><stop offset="0.45" stop-color="{ACCENT}"/>'
        f'<stop offset="1" stop-color="{SECONDARY}"/></linearGradient>'
        f'<radialGradient id="bg" cx="54" cy="46" r="62" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="#1A1F33"/><stop offset="1" stop-color="{BG_DARK}"/></radialGradient>'
        f'<radialGradient id="halo" cx="54" cy="54" r="34" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{ACCENT}" stop-opacity="0.28"/>'
        f'<stop offset="1" stop-color="{ACCENT}" stop-opacity="0"/></radialGradient>')

K_LAUNCHER = 0.8      # design radius 30 -> 24 on the 108 canvas


def layers(key, fn):
    ox, oy = OFFSET[key]
    k = K_LAUNCHER * FIT[key]
    cx, cy = 54 + ox * k, 54 + oy * k
    shapes = fn()
    background = svg(108, 108, '<rect width="108" height="108" fill="url(#bg)"/>'
                     '<circle cx="54" cy="54" r="34" fill="url(#halo)"/>', GRAD)
    glow = place(shapes, cx, cy, k, ACCENT, 2.4).replace("<g ", '<g opacity="0.10" ', 1)
    foreground = svg(108, 108, glow + place(shapes, cx, cy, k, "url(#ink)"), GRAD)
    monochrome = svg(108, 108, place(shapes, cx, cy, k, "#FFFFFF"))
    nk = 10 / 30 * FIT[key]
    notif = svg(24, 24, place(fn(w=1.08), 12 + ox * nk, 12 + oy * nk, nk, "#FFFFFF"))
    return background, foreground, monochrome, notif


# ---------------------------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------------------------
def render(jobs):
    jf = os.path.join(TMP, "jobs.json")
    with open(jf, "w") as fh:
        json.dump(jobs, fh)
    subprocess.run(["node", os.path.join(HERE, "render.js"), jf], check=True)


def mask(shape, size, ss=4):
    S = size * ss
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse([0, 0, S - 1, S - 1], fill=255)
    elif shape == "rounded":
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.23), fill=255)
    elif shape == "teardrop":
        d.ellipse([0, 0, S - 1, S - 1], fill=255)
        d.rounded_rectangle([S // 2, S // 2, S - 1, S - 1], radius=int(S * 0.1), fill=255)
    elif shape == "squircle":
        n = 5.0
        pts = []
        for i in range(720):
            t = 2 * math.pi * i / 720
            c, s_ = math.cos(t), math.sin(t)
            x = math.copysign(abs(c) ** (2 / n), c)
            y = math.copysign(abs(s_) ** (2 / n), s_)
            pts.append(((x + 1) / 2 * (S - 1), (y + 1) / 2 * (S - 1)))
        d.polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def adaptive(bg_png, fg_png, size, shape):
    """Composite the 108 canvas, keep the centre 72 (what launchers show), apply the mask."""
    full = Image.alpha_composite(Image.open(bg_png).convert("RGBA"), Image.open(fg_png).convert("RGBA"))
    W = full.width
    crop = full.crop((W * 18 // 108, W * 18 // 108, W * 90 // 108, W * 90 // 108))
    crop = crop.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(crop, (0, 0), mask(shape, size))
    return out


def themed(mono_png, size, shape, dark):
    mono = Image.open(mono_png).convert("RGBA")
    W = mono.width
    a = mono.split()[3].crop((W * 18 // 108, W * 18 // 108, W * 90 // 108, W * 90 // 108))
    a = a.resize((size, size), Image.LANCZOS)
    bg, fg = ((0x2E, 0x2C, 0x3A), (0xD7, 0xCF, 0xF5)) if dark else ((0xE9, 0xE2, 0xFB), (0x3A, 0x2E, 0x6B))
    tile = Image.new("RGBA", (size, size), bg + (255,))
    tile.paste(Image.new("RGBA", (size, size), fg + (255,)), (0, 0), a)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(tile, (0, 0), mask(shape, size))
    return out


def wallpaper(w, h, dark):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    top, bot = ((22, 26, 40), (8, 10, 16)) if dark else ((226, 233, 244), (246, 240, 230))
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    blob = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(blob)
    cols = [(56, 189, 248, 70), (244, 114, 182, 60)] if dark else [(129, 140, 248, 70), (251, 191, 36, 60)]
    bd.ellipse([w * 0.55, -h * 0.4, w * 1.1, h * 0.7], fill=cols[0])
    bd.ellipse([-w * 0.1, h * 0.45, w * 0.35, h * 1.4], fill=cols[1])
    blob = blob.filter(ImageFilter.GaussianBlur(60))
    return Image.alpha_composite(img.convert("RGBA"), blob)


def font(size, bold=False):
    for name in (["segoeuib.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def tinted(png, size, rgb):
    a = Image.open(png).convert("RGBA").split()[3].resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), rgb + (0,))
    out.putalpha(a)
    return out


def concept_block(key, name, files, width):
    bg, fg, mono, notif24, notif72 = files
    masks = ["circle", "squircle", "rounded", "teardrop"]
    H = 860
    block = Image.new("RGBA", (width, H), (240, 242, 246, 255))
    d = ImageDraw.Draw(block)
    d.text((24, 14), f"Concept {key.upper()} — {name}", fill=(15, 23, 42), font=font(30, True))
    y = 64
    for dark in (True, False):
        wp = wallpaper(width - 48, 250, dark)
        x = 24
        for m in masks:
            wp.alpha_composite(adaptive(bg, fg, 192, m), (x, 29))
            x += 208
        x += 8
        for m in masks:
            wp.alpha_composite(adaptive(bg, fg, 48, m), (x, 101))
            x += 64
        x += 16
        wp.alpha_composite(themed(mono, 192, "circle", dark), (x, 29))
        x += 208
        wp.alpha_composite(themed(mono, 48, "circle", dark), (x, 101))
        block.alpha_composite(wp, (24, y))
        y += 262
    # watch row
    row = Image.new("RGBA", (width - 48, 300), (226, 230, 238, 255))
    rd = ImageDraw.Draw(row)
    # Wear OS app list: round icon ~52 dp on a 240 dp (480 px) screen -> show at 1:1 px, 104 px
    def watch(face, label, x):
        disc = Image.new("RGBA", (280, 280), (0, 0, 0, 0))
        ImageDraw.Draw(disc).ellipse([0, 0, 279, 279], fill=(0, 0, 0, 255), outline=(60, 64, 76, 255), width=6)
        disc.alpha_composite(face, (0, 0))
        row.alpha_composite(disc, (x, 8))
        rd.text((x + 140, 292), label, fill=(51, 65, 85), font=font(15), anchor="ms")
    face = Image.new("RGBA", (280, 280), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face)
    icon = adaptive(bg, fg, 84, "circle")
    for i, yy in enumerate((26, 118, 210)):
        face.alpha_composite(icon if i == 1 else Image.new("RGBA", (84, 84)), (44, yy - 14))
        if i != 1:
            fd.ellipse([44, yy - 14, 128, yy + 70], fill=(40, 44, 56, 255))
        fd.text((142, yy + 28), "Voxara" if i == 1 else ("Calendar" if i == 0 else "Weather"),
                fill=(240, 240, 245), font=font(26, True), anchor="lm")
    watch(face, "Wear OS app list (1:1 px, 480 px screen)", 0)
    splash = Image.new("RGBA", (280, 280), (0, 0, 0, 0))
    m = Image.open(fg).convert("RGBA").resize((160, 160), Image.LANCZOS)
    splash.alpha_composite(m, (60, 60))
    watch(splash, "Watch splash (mark on black)", 300)
    # notification silhouettes
    x = 620
    bar = Image.new("RGBA", (300, 110), (24, 26, 34, 255))
    bd = ImageDraw.Draw(bar)
    bar.alpha_composite(tinted(notif72, 72, (255, 255, 255)), (16, 19))
    bar.alpha_composite(tinted(notif24, 24, (255, 255, 255)), (110, 43))
    bar.alpha_composite(tinted(notif24, 24, (210, 210, 220)), (150, 43))
    bd.text((190, 55), "24 dp @1x / @3x", fill=(200, 200, 210), font=font(15), anchor="lm")
    row.alpha_composite(bar, (x, 20))
    lightbar = Image.new("RGBA", (300, 70), (246, 247, 250, 255))
    lightbar.alpha_composite(tinted(notif24, 24, (30, 30, 40)), (16, 23))
    lightbar.alpha_composite(tinted(notif72, 48, (30, 30, 40)), (60, 11))
    row.alpha_composite(lightbar, (x, 140))
    # pixel-level 24 px magnified
    big = tinted(notif24, 24, (255, 255, 255))
    zoom = Image.new("RGBA", (24, 24), (24, 26, 34, 255))
    zoom.alpha_composite(big)
    zoom = zoom.resize((192, 192), Image.NEAREST)
    row.alpha_composite(zoom, (940, 10))
    rd.text((1036, 222), "24 px, 8× zoom", fill=(51, 65, 85), font=font(15), anchor="ms")
    mono_preview = Image.new("RGBA", (192, 192), (24, 26, 34, 255))
    mono_preview.alpha_composite(themed(mono, 192, "circle", True))
    row.alpha_composite(themed(mono, 150, "squircle", False), (1160, 20))
    rd.text((1235, 222), "Monochrome (themed, light)", fill=(51, 65, 85), font=font(15), anchor="ms")
    block.alpha_composite(row, (24, y + 4))
    return block


def main():
    os.makedirs(TMP, exist_ok=True)
    jobs, files = [], {}
    for key, name, fn in CONCEPTS:
        bg, fg, mono, notif = layers(key, fn)
        paths = {}
        for part, content in (("background", bg), ("foreground", fg), ("monochrome", mono),
                              ("notification", notif)):
            p = os.path.join(OUT, f"concept-{key}-{part}.svg")
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(content)
            paths[part] = p
        # combined preview SVG (foreground over background) for quick viewing
        combo = svg(108, 108, bg.split("</defs>", 1)[1][:-6] + fg.split("</defs>", 1)[1][:-6], GRAD)
        with open(os.path.join(OUT, f"concept-{key}.svg"), "w", encoding="utf-8") as fh:
            fh.write(combo)
        r = {}
        for part, width in (("background", 648), ("foreground", 648), ("monochrome", 648)):
            r[part] = os.path.join(TMP, f"{key}-{part}.png")
            jobs.append({"svg": paths[part], "png": r[part], "width": width})
        for width in (24, 72):
            r[f"n{width}"] = os.path.join(TMP, f"{key}-notif{width}.png")
            jobs.append({"svg": paths["notification"], "png": r[f"n{width}"], "width": width})
        files[key] = (r["background"], r["foreground"], r["monochrome"], r["n24"], r["n72"])
    render(jobs)
    width = 1420
    blocks = [concept_block(k, n, files[k], width) for k, n, _ in CONCEPTS]
    sheet = Image.new("RGBA", (width, sum(b.height for b in blocks) + 20 * (len(blocks) - 1)), (255, 255, 255, 255))
    y = 0
    for b in blocks:
        sheet.alpha_composite(b, (0, y))
        y += b.height + 20
    sheet.convert("RGB").save(os.path.join(OUT, "compare.png"), optimize=True)
    print("compare.png written")


if __name__ == "__main__":
    sys.exit(main())
