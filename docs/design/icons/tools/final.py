"""Voxara icon set (concept A, "Gauge V"): source SVGs, Android VectorDrawables, PNGs, previews.

One geometry (the 270° dose arc, open at the bottom, and a V whose point drops through the opening)
is written twice from the same item list: as SVG (docs/design/icons/final/) and as VectorDrawable XML
(app/ and phone/ res). The previews are rendered from the shipped VectorDrawable XML, parsed back
into SVG, so what is checked is what is installed.

Run from anywhere:
    npm install --prefix <dir> @resvg/resvg-js
    NODE_PATH=<dir>/node_modules python docs/design/icons/tools/final.py
"""
import math
import os
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import concepts as C   # mask(), adaptive(), themed(), wallpaper(), font(), tinted(), render()

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))
FINAL = os.path.join(ROOT, "docs", "design", "icons", "final")
STORE = os.path.join(ROOT, "docs", "design", "icons", "store")
TMP = os.path.join(FINAL, "_render")
APP_RES = os.path.join(ROOT, "app", "src", "main", "res")
PHONE_RES = os.path.join(ROOT, "phone", "src", "main", "res")
FONT_JAKARTA = os.path.join(ROOT, "shared-ui", "src", "main", "res", "font", "plus_jakarta_sans.ttf")
FONT_MONO = os.path.join(ROOT, "shared-ui", "src", "main", "res", "font", "jetbrains_mono.ttf")

BG_DARK = "#090B10"
BG_LIFT = "#1A1F33"
ACCENT = "#8B5CF6"
INK_STOPS = [(0.0, "#C4B5FD"), (0.45, "#8B5CF6"), (1.0, "#6366F1")]

f = C.f


# ---------------------------------------------------------------------------------------------
# Geometry: the mark in design units (outer radius ~29, centred), baked into a target frame.
# ---------------------------------------------------------------------------------------------
RING_R, RING_W = 25.5, 7.0
V_PTS = [(-12.5, -10.0), (0.0, 19.0), (12.5, -10.0)]
OPTICAL_DY = 2.5          # the mark's bounding box sits high; this centres it optically


def mark_paths(cx, cy, k, w=1.0):
    """[(pathData, strokeWidth)] for the ring and the V, centred at (cx, cy), scale k."""
    oy = cy + OPTICAL_DY * k
    r = RING_R * k
    x0, y0 = cx + r * math.cos(math.radians(135)), oy + r * math.sin(math.radians(135))
    x1, y1 = cx + r * math.cos(math.radians(45)), oy + r * math.sin(math.radians(45))
    ring = f"M{f(x0)},{f(y0)}A{f(r)},{f(r)} 0 1,1 {f(x1)},{f(y1)}"
    v = "M" + "L".join(f"{f(cx + x * k)},{f(oy + y * k)}" for x, y in V_PTS)
    sw = RING_W * k * w
    return [(ring, sw), (v, sw)]


# Placement per target (canvas, centre, scale, stroke factor)
PHONE = dict(cx=54, cy=54, k=0.80, w=1.0)       # mark inside r ~ 23.5 of the 33 dp safe zone
WATCH = dict(cx=54, cy=54, k=0.86, w=1.08)      # simplified for the small round screen: bigger, bolder
NOTIF = dict(cx=12, cy=12, k=0.3467, w=1.0)     # 24 dp, live area 20 dp, stroke ~2.4 dp


# ---------------------------------------------------------------------------------------------
# Item model -> SVG and VectorDrawable
#   item = dict(d=pathData, fill=paint | None, stroke=paint | None, width, alpha)
#   paint = "#RRGGBB" | ("linear", x1, y1, x2, y2, stops) | ("radial", cx, cy, r, stops)
#   stops = [(offset, "#RRGGBB", alpha)]
# ---------------------------------------------------------------------------------------------
def ink_gradient():
    return ("linear", 30, 28, 78, 82, [(o, c, 1.0) for o, c in INK_STOPS])


def circle_d(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}A{f(r)},{f(r)} 0 1,1 {f(cx + r)},{f(cy)}A{f(r)},{f(r)} 0 1,1 {f(cx - r)},{f(cy)}Z"


def background_items():
    return [
        dict(d="M0,0h108v108h-108z", fill=("radial", 54, 46, 62, [(0, BG_LIFT, 1), (1, BG_DARK, 1)])),
        dict(d=circle_d(54, 54, 34), fill=("radial", 54, 54, 34, [(0, ACCENT, 0.34), (0.55, ACCENT, 0.12), (1, ACCENT, 0)])),
    ]


def foreground_items(p):
    # No stroke "glow" on the mark: at 192-512 px a wider translucent stroke reads as a hard ghost
    # outline. The glow is the background's soft radial halo instead.
    return [dict(d=d, stroke=ink_gradient(), width=sw) for d, sw in mark_paths(**p)]


def solid_items(p, color="#FFFFFF"):
    return [dict(d=d, stroke=color, width=sw) for d, sw in mark_paths(**p)]


def _hex_alpha(c, a):
    return f"#{round(a * 255):02X}{c[1:].upper()}"


def to_svg(size, viewport, items):
    defs, body = [], []
    for i, it in enumerate(items):
        attrs = [f'd="{it["d"]}"']
        for role in ("fill", "stroke"):
            paint = it.get(role)
            if paint is None:
                attrs.append(f'{role}="none"')
            elif isinstance(paint, str):
                attrs.append(f'{role}="{paint}"')
            else:
                gid = f"g{i}{role[0]}"
                stops = "".join(f'<stop offset="{f(o)}" stop-color="{c}" stop-opacity="{f(a)}"/>' for o, c, a in paint[-1])
                if paint[0] == "linear":
                    _, x1, y1, x2, y2, _s = paint
                    defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" '
                                f'x2="{f(x2)}" y2="{f(y2)}">{stops}</linearGradient>')
                else:
                    _, gx, gy, r, _s = paint
                    defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(gx)}" cy="{f(gy)}" '
                                f'r="{f(r)}">{stops}</radialGradient>')
                attrs.append(f'{role}="url(#{gid})"')
        if it.get("stroke") is not None:
            attrs.append(f'stroke-width="{f(it["width"])}" stroke-linecap="round" stroke-linejoin="round"')
        if "alpha" in it:
            attrs.append(f'opacity="{f(it["alpha"])}"')
        body.append(f"<path {' '.join(attrs)}/>")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {viewport} {viewport}">'
            f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>\n')


def to_vd(size_dp, viewport, items, comment, tint=None):
    uses_gradient = any(not isinstance(it.get(r), (str, type(None))) for it in items for r in ("fill", "stroke"))
    ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    if uses_gradient:
        ns += '\n    xmlns:aapt="http://schemas.android.com/aapt"'
    out = [f"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- {comment}\n     Generated by docs/design/icons/tools/final.py; edit the generator, not this file. -->",
           f"<vector {ns}\n    android:width=\"{size_dp}dp\"\n    android:height=\"{size_dp}dp\"\n"
           f"    android:viewportWidth=\"{viewport}\"\n    android:viewportHeight=\"{viewport}\""
           + (f"\n    android:tint=\"{tint}\"" if tint else "") + ">"]
    for it in items:
        attrs = [f'android:pathData="{it["d"]}"']
        children = []
        for role, attr in (("fill", "android:fillColor"), ("stroke", "android:strokeColor")):
            paint = it.get(role)
            if paint is None:
                continue
            if isinstance(paint, str):
                attrs.append(f'{attr}="{paint.upper()}"')
            else:
                items_xml = "".join(
                    f'\n                <item android:offset="{f(o)}" android:color="{_hex_alpha(c, a)}" />' for o, c, a in paint[-1])
                if paint[0] == "linear":
                    _, x1, y1, x2, y2, _s = paint
                    g = (f'android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
                         f'android:endX="{f(x2)}" android:endY="{f(y2)}"')
                else:
                    _, gx, gy, r, _s = paint
                    g = f'android:type="radial" android:centerX="{f(gx)}" android:centerY="{f(gy)}" android:gradientRadius="{f(r)}"'
                children.append(f'\n        <aapt:attr name="{attr}">\n            <gradient {g}>{items_xml}\n'
                                f'            </gradient>\n        </aapt:attr>')
        if it.get("stroke") is not None:
            attrs += [f'android:strokeWidth="{f(it["width"])}"', 'android:strokeLineCap="round"',
                      'android:strokeLineJoin="round"']
            if "alpha" in it:
                attrs.append(f'android:strokeAlpha="{f(it["alpha"])}"')
        elif "alpha" in it:
            attrs.append(f'android:fillAlpha="{f(it["alpha"])}"')
        sep = "\n        "
        if children:
            out.append(f"    <path{sep}{sep.join(attrs)}>{''.join(children)}\n    </path>")
        else:
            out.append(f"    <path{sep}{sep.join(attrs)} />")
    out.append("</vector>\n")
    return "\n".join(out)


# ---------------------------------------------------------------------------------------------
# VectorDrawable XML -> SVG (to preview exactly what ships)
# ---------------------------------------------------------------------------------------------
A = "{http://schemas.android.com/apk/res/android}"


def _argb(c):
    c = c.lstrip("#")
    if len(c) == 6:
        return "#" + c, 1.0
    return "#" + c[2:], int(c[:2], 16) / 255


def vd_to_svg(path):
    root = ET.parse(path).getroot()
    vw = float(root.get(A + "viewportWidth"))
    defs, body = [], []
    for i, p in enumerate(root.iter("path")):
        attrs = [f'd="{p.get(A + "pathData")}"']
        paints = {"fill": p.get(A + "fillColor"), "stroke": p.get(A + "strokeColor")}
        for child in p:
            name = child.get("name")
            g = child.find("gradient")
            role = "fill" if name == "android:fillColor" else "stroke"
            gid = f"v{i}{role[0]}"
            stops = ""
            for it in g.findall("item"):
                col, a = _argb(it.get(A + "color"))
                stops += f'<stop offset="{it.get(A + "offset")}" stop-color="{col}" stop-opacity="{a:.3f}"/>'
            if g.get(A + "type") == "linear":
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX")}" '
                            f'y1="{g.get(A + "startY")}" x2="{g.get(A + "endX")}" y2="{g.get(A + "endY")}">{stops}</linearGradient>')
            else:
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A + "centerX")}" '
                            f'cy="{g.get(A + "centerY")}" r="{g.get(A + "gradientRadius")}">{stops}</radialGradient>')
            paints[role] = f"url(#{gid})"
        for role, val in paints.items():
            if val is None:
                attrs.append(f'{role}="none"')
            elif val.startswith("url"):
                attrs.append(f'{role}="{val}"')
            else:
                col, a = _argb(val)
                a *= float(p.get(A + ("strokeAlpha" if role == "stroke" else "fillAlpha"), 1))
                attrs.append(f'{role}="{col}" {role}-opacity="{a:.3f}"')
        if paints["stroke"]:
            attrs.append(f'stroke-width="{p.get(A + "strokeWidth")}" stroke-linecap="{p.get(A + "strokeLineCap", "butt")}" '
                         f'stroke-linejoin="{p.get(A + "strokeLineJoin", "miter")}"')
        body.append(f"<path {' '.join(attrs)}/>")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(vw)}" height="{f(vw)}" viewBox="0 0 {f(vw)} {f(vw)}">'
            f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>')


# ---------------------------------------------------------------------------------------------
# Writers
# ---------------------------------------------------------------------------------------------
def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def write_sources():
    write(os.path.join(FINAL, "voxara-mark.svg"), to_svg(108, 108, foreground_items(PHONE)))
    write(os.path.join(FINAL, "launcher-background.svg"), to_svg(108, 108, background_items()))
    write(os.path.join(FINAL, "launcher-foreground.svg"), to_svg(108, 108, foreground_items(PHONE)))
    write(os.path.join(FINAL, "launcher-foreground-watch.svg"), to_svg(108, 108, foreground_items(WATCH)))
    write(os.path.join(FINAL, "launcher-monochrome.svg"), to_svg(108, 108, solid_items(PHONE)))
    write(os.path.join(FINAL, "launcher-monochrome-watch.svg"), to_svg(108, 108, solid_items(WATCH)))
    write(os.path.join(FINAL, "notification.svg"), to_svg(24, 24, solid_items(NOTIF)))
    write(os.path.join(FINAL, "voxara-icon.svg"),
          to_svg(108, 108, background_items() + foreground_items(PHONE)))


def write_android():
    for res, p, who in ((PHONE_RES, PHONE, "phone"), (APP_RES, WATCH, "watch")):
        write(os.path.join(res, "drawable", "ic_launcher_background.xml"),
              to_vd(108, 108, background_items(), f"Voxara launcher background ({who}): #090B10 lifted to #1A1F33, soft accent halo."))
        write(os.path.join(res, "drawable", "ic_launcher_foreground.xml"),
              to_vd(108, 108, foreground_items(p),
                    f"Voxara mark ({who}): 270° dose arc + V needle, inside the 66 dp safe zone. "
                    "Also the splash icon: the mark sits inside the splash's visible 2/3 circle."))
        write(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"),
              to_vd(108, 108, solid_items(p), f"Voxara themed-icon layer ({who}): the mark's silhouette, alpha only."))
        write(os.path.join(res, "drawable", "ic_voxara_status.xml"),
              to_vd(24, 24, solid_items(NOTIF),
                    "Voxara notification / complication icon: white silhouette, 24 dp, no gradients.", tint=None))
    for name in ("ic_launcher", "ic_launcher_round"):
        write(os.path.join(PHONE_RES, "mipmap-anydpi", f"{name}.xml"),
              ADAPTIVE.format(comment="Adaptive launcher icon (minSdk 28, so no -v26 qualifier is needed). Layers: 108 dp, safe zone 66 dp."))
        write(os.path.join(APP_RES, "mipmap-anydpi", f"{name}.xml"),
              ADAPTIVE.format(comment="Wear OS launcher icon: adaptive, shown through the round mask (minSdk 30)."))


# ---------------------------------------------------------------------------------------------
# Raster outputs
# ---------------------------------------------------------------------------------------------
def jakarta(size, weight=800):
    ft = ImageFont.truetype(FONT_JAKARTA, size)
    try:
        ft.set_variation_by_axes([weight])
    except Exception:
        pass
    return ft


def mono(size, weight=700):
    ft = ImageFont.truetype(FONT_MONO, size)
    try:
        ft.set_variation_by_axes([weight])
    except Exception:
        pass
    return ft


def render_all():
    os.makedirs(TMP, exist_ok=True)
    srcs = {
        "p_bg": os.path.join(PHONE_RES, "drawable", "ic_launcher_background.xml"),
        "p_fg": os.path.join(PHONE_RES, "drawable", "ic_launcher_foreground.xml"),
        "p_mono": os.path.join(PHONE_RES, "drawable", "ic_launcher_monochrome.xml"),
        "w_bg": os.path.join(APP_RES, "drawable", "ic_launcher_background.xml"),
        "w_fg": os.path.join(APP_RES, "drawable", "ic_launcher_foreground.xml"),
        "w_mono": os.path.join(APP_RES, "drawable", "ic_launcher_monochrome.xml"),
        "notif": os.path.join(PHONE_RES, "drawable", "ic_voxara_status.xml"),
        "notif_w": os.path.join(APP_RES, "drawable", "ic_voxara_status.xml"),
    }
    jobs, out = [], {}
    for key, vd in srcs.items():
        svgp = os.path.join(TMP, key + ".svg")
        write(svgp, vd_to_svg(vd))
        widths = (24, 72) if key.startswith("notif") else (1536,)
        for wpx in widths:
            png = os.path.join(TMP, f"{key}_{wpx}.png")
            jobs.append({"svg": svgp, "png": png, "width": wpx})
            out[f"{key}_{wpx}"] = png
    C.TMP = TMP
    C.render(jobs)
    return out


def full_square(bg, fg, size):
    """The visible 72 dp of the adaptive canvas as a full square (Play applies its own mask)."""
    full = Image.alpha_composite(Image.open(bg).convert("RGBA"), Image.open(fg).convert("RGBA"))
    W = full.width
    return full.crop((W * 18 // 108, W * 18 // 108, W * 90 // 108, W * 90 // 108)).resize((size, size), Image.LANCZOS)


def store_assets(r):
    os.makedirs(STORE, exist_ok=True)
    full_square(r["p_bg_1536"], r["p_fg_1536"], 512).convert("RGB").save(os.path.join(STORE, "play-icon-512.png"))
    full_square(r["w_bg_1536"], r["w_fg_1536"], 512).convert("RGB").save(os.path.join(STORE, "play-icon-wear-512.png"))
    # Feature graphic 1024 x 500: template style (dark, bezel arc, wave lines, Mono numbers).
    W, H = 1024, 500
    img = Image.new("RGBA", (W, H), (9, 11, 16, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([40, 20, 500, 480], fill=(139, 92, 246, 70))
    gd.ellipse([620, -200, 1200, 260], fill=(99, 102, 241, 40))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(90)))
    # wave lines (3 sines with an edge envelope, alphas as in WaveLines)
    waves = Image.new("RGBA", (W * 2, H * 2), (0, 0, 0, 0))
    wd = ImageDraw.Draw(waves)
    for amp, alpha, phase, yy in ((34, 70, 0.0, 448), (22, 32, 1.4, 458), (16, 32, 2.6, 440)):
        pts = []
        for x in range(0, W * 2 + 1, 4):
            t = x / (W * 2)
            env = math.sin(math.pi * t) ** 1.5
            pts.append((x, yy * 2 + amp * 2 * env * math.sin(2 * math.pi * 2.2 * t + phase)))
        wd.line(pts, fill=(167, 139, 250, alpha), width=5, joint="curve")
    img.alpha_composite(waves.resize((W, H), Image.LANCZOS))
    mark = Image.open(r["p_fg_1536"]).convert("RGBA").resize((600, 600), Image.LANCZOS)
    halo = Image.new("RGBA", mark.size, (139, 92, 246, 0))
    halo.putalpha(mark.split()[3].point(lambda a: a * 0.55).filter(ImageFilter.GaussianBlur(22)))
    img.alpha_composite(halo, (-40, -55))
    img.alpha_composite(mark, (-40, -55))
    d = ImageDraw.Draw(img)
    d.text((500, 150), "Voxara", font=jakarta(92, 800), fill=(248, 250, 252))
    d.text((504, 268), "Your hearing, measured.", font=jakarta(34, 700), fill=(167, 139, 250))
    d.text((504, 318), "Sound level and daily dose on your wrist,", font=jakarta(24, 500), fill=(174, 182, 196))
    d.text((504, 350), "with a warning before it becomes harmful.", font=jakarta(24, 500), fill=(174, 182, 196))
    img.convert("RGB").save(os.path.join(STORE, "feature-graphic-1024x500.png"))


# ---------------------------------------------------------------------------------------------
# Tile previews (Wear OS tile picker): a drawn likeness of each tile's real layout and copy.
# ---------------------------------------------------------------------------------------------
def tile_preview(title, value, detail, sub, fraction, tint, round_):
    S, ss = 384, 3
    img = Image.new("RGBA", (S * ss, S * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_:
        d.ellipse([0, 0, S * ss - 1, S * ss - 1], fill=(0, 0, 0, 255))
    else:
        d.rectangle([0, 0, S * ss, S * ss], fill=(0, 0, 0, 255))
    th = 16 * ss                     # 8 dp at the preview's 2 px/dp
    inset = 6 * ss + th // 2
    box = [inset, inset, S * ss - inset, S * ss - inset]

    def arc(sweep, color):
        # ProtoLayout anchor 225° (0 = 12 o'clock) = Pillow 135° (0 = 3 o'clock), clockwise
        d.arc(box, 135, 135 + sweep, fill=color, width=th)
        for ang in (135, 135 + sweep):   # round caps
            cx = S * ss / 2 + (box[2] - box[0]) / 2 * math.cos(math.radians(ang))
            cy = S * ss / 2 + (box[3] - box[1]) / 2 * math.sin(math.radians(ang))
            d.ellipse([cx - th / 2, cy - th / 2, cx + th / 2, cy + th / 2], fill=color)
    arc(270, (31, 31, 31, 255))
    arc(270 * fraction, tint)
    cx = S * ss // 2
    d.text((cx, 128 * ss), title, font=C.font(23 * ss, True), fill=(174, 182, 196), anchor="mm")
    d.text((cx, 190 * ss), value, font=C.font(70 * ss, True), fill=(248, 250, 252), anchor="mm")
    d.text((cx, 246 * ss), detail, font=C.font(25 * ss, True), fill=tint, anchor="mm")
    d.text((cx, 282 * ss), sub, font=C.font(23 * ss), fill=(174, 182, 196), anchor="mm")
    return img.resize((S, S), Image.LANCZOS)


def write_tile_previews():
    purple = (167, 139, 250, 255)
    for round_, folder in ((False, "drawable-nodpi"), (True, "drawable-round-nodpi")):
        d = os.path.join(APP_RES, folder)
        os.makedirs(d, exist_ok=True)
        tile_preview("TODAY'S DOSE", "46%", "Moderate · 72 dBA", "Updated 14:05", 0.46, purple, round_) \
            .save(os.path.join(d, "tile_preview_dose.png"), optimize=True)
        tile_preview("LISTENING · WEEK", "38%", "OK", "ESTIMATED", 0.38, purple, round_) \
            .save(os.path.join(d, "tile_preview_listening.png"), optimize=True)


# ---------------------------------------------------------------------------------------------
# Final preview sheet
# ---------------------------------------------------------------------------------------------
def home_screen(r, dark, themed_icons):
    W, H = 600, 420
    wp = C.wallpaper(W, H, dark)
    d = ImageDraw.Draw(wp)
    others = [(66, 133, 244), (52, 168, 83), (251, 188, 5), (234, 67, 53), (0, 150, 136), (156, 39, 176), (255, 112, 67)]
    labels = ["Phone", "Maps", "Photos", "Mail", "Fit", "Voxara", "Music", "Clock"]
    size, gap = 112, 32
    x0 = (W - (4 * size + 3 * gap)) // 2
    fg_txt = (240, 242, 246) if dark else (20, 24, 32)
    for i, lab in enumerate(labels):
        x, y = x0 + (i % 4) * (size + gap), 40 + (i // 4) * 170
        if lab == "Voxara":
            ic = C.themed(r["p_mono_1536"], size, "circle", dark) if themed_icons else \
                C.adaptive(r["p_bg_1536"], r["p_fg_1536"], size, "circle")
        else:
            col = others[i % len(others)]
            ic = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            m = C.mask("circle", size)
            if themed_icons:
                bg = (0x2E, 0x2C, 0x3A) if dark else (0xE9, 0xE2, 0xFB)
                tile = Image.new("RGBA", (size, size), bg + (255,))
                ImageDraw.Draw(tile).rounded_rectangle([34, 34, size - 34, size - 34], 10,
                                                        outline=(0xD7, 0xCF, 0xF5) if dark else (0x3A, 0x2E, 0x6B), width=6)
            else:
                tile = Image.new("RGBA", (size, size), col + (255,))
                ImageDraw.Draw(tile).ellipse([36, 36, size - 36, size - 36], fill=(255, 255, 255, 220))
            ic.paste(tile, (0, 0), m)
        wp.alpha_composite(ic, (x, y))
        d.text((x + size // 2, y + size + 22), lab, font=C.font(20, True), fill=fg_txt, anchor="mm")
    return wp


def watch_list(r):
    S = 360
    face = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(face).ellipse([0, 0, S - 1, S - 1], fill=(0, 0, 0, 255))
    d = ImageDraw.Draw(face)
    ic = C.adaptive(r["w_bg_1536"], r["w_fg_1536"], 96, "circle")   # 48 dp at 2 px/dp
    rows = [("Alarm", (234, 67, 53)), ("Voxara", None), ("Weather", (66, 133, 244))]
    for i, (lab, col) in enumerate(rows):
        y = 42 + i * 100
        if col is None:
            face.alpha_composite(ic, (52, y))
        else:
            d.ellipse([52, y, 148, y + 96], fill=col + (255,))
        d.text((168, y + 48), lab, font=C.font(30, True), fill=(240, 240, 245), anchor="lm")
    m = C.mask("circle", S)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(face, (0, 0), m)
    return out


def watch_splash(r):
    S = 360   # 180 dp face at 2 px/dp; splash icon 90 dp -> 180 px, visible 2/3 circle
    face = Image.new("RGBA", (S, S), (0, 0, 0, 255))
    icon = Image.open(r["w_fg_1536"]).convert("RGBA").resize((180, 180), Image.LANCZOS)
    vis = Image.new("RGBA", (180, 180), (0, 0, 0, 0))
    vis.paste(icon, (0, 0), _inner_mask(180))
    face.alpha_composite(vis, (90, 90))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(face, (0, 0), C.mask("circle", S))
    return out


def _inner_mask(size):
    """The splash shows the centre 2/3 of the icon through a circle."""
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse([size * 4 / 6, size * 4 / 6, size * 4 * 5 / 6, size * 4 * 5 / 6], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def phone_splash(r, dark):
    W, H = 260, 420
    bg = (9, 11, 16) if dark else (243, 245, 249)
    img = Image.new("RGBA", (W, H), bg + (255,))
    icon = Image.open(r["p_fg_1536"]).convert("RGBA").resize((216, 216), Image.LANCZOS)  # 288 dp scaled
    vis = Image.new("RGBA", (216, 216), (0, 0, 0, 0))
    vis.paste(icon, (0, 0), _inner_mask(216))
    img.alpha_composite(vis, ((W - 216) // 2, (H - 216) // 2))
    return img


def notification_row(r, dark):
    W, H = 600, 200
    bg = (32, 33, 40) if dark else (242, 243, 247)
    card = (44, 46, 56) if dark else (255, 255, 255)
    txt = (236, 238, 242) if dark else (20, 24, 32)
    sub = (170, 176, 190) if dark else (90, 98, 112)
    img = Image.new("RGBA", (W, H), bg + (255,))
    d = ImageDraw.Draw(img)
    # status bar: time + small icon at 24 dp @ 2.5 px/dp ~ 60 px? use 48 px (xhdpi-ish)
    d.text((16, 22), "14:05", font=C.font(22, True), fill=txt, anchor="lm")
    img.alpha_composite(C.tinted(r["notif_72"], 30, txt[:3]), (86, 7))
    d.rounded_rectangle([12, 52, W - 12, H - 12], 26, fill=card)
    circ = Image.new("RGBA", (56, 56), (0, 0, 0, 0))
    ImageDraw.Draw(circ).ellipse([0, 0, 55, 55], fill=(139, 92, 246, 255))
    circ.alpha_composite(C.tinted(r["notif_72"], 34, (255, 255, 255)), (11, 11))
    img.alpha_composite(circ, (30, 72))
    d.text((104, 86), "Voxara · now", font=C.font(18), fill=sub, anchor="lm")
    d.text((104, 116), "Monitoring sound · Today 46 %", font=C.font(22, True), fill=txt, anchor="lm")
    d.text((104, 146), "Moderate · 72 dBA", font=C.font(19), fill=sub, anchor="lm")
    return img


def label(img, text, dark_bg=False):
    out = Image.new("RGBA", (img.width, img.height + 34), (255, 255, 255, 0))
    out.alpha_composite(img, (0, 0))
    ImageDraw.Draw(out).text((img.width // 2, img.height + 18), text, font=C.font(17), fill=(51, 65, 85), anchor="mm")
    return out


def preview_sheet(r):
    tiles = [
        label(home_screen(r, False, False), "Phone launcher, light wallpaper"),
        label(home_screen(r, True, False), "Phone launcher, dark wallpaper"),
        label(home_screen(r, False, True), "Themed icons (Android 13+), light"),
        label(home_screen(r, True, True), "Themed icons (Android 13+), dark"),
    ]
    masks = Image.new("RGBA", (600, 250), (226, 230, 238, 255))
    for i, m in enumerate(("circle", "squircle", "rounded", "teardrop")):
        masks.alpha_composite(C.adaptive(r["p_bg_1536"], r["p_fg_1536"], 120, m), (24 + i * 144, 20))
        masks.alpha_composite(C.adaptive(r["p_bg_1536"], r["p_fg_1536"], 48, m), (60 + i * 144, 170))
    tiles.append(label(masks, "Adaptive masks at 120 px and 48 px"))
    notif_zoom = Image.new("RGBA", (600, 250), (226, 230, 238, 255))
    for i, (key, name) in enumerate((("notif_24", "phone"), ("notif_w_24", "watch"))):
        z = Image.new("RGBA", (24, 24), (24, 26, 34, 255))
        z.alpha_composite(C.tinted(r[key], 24, (255, 255, 255)))
        notif_zoom.alpha_composite(z.resize((192, 192), Image.NEAREST), (40 + i * 280, 20))
        ImageDraw.Draw(notif_zoom).text((136 + i * 280, 232), f"24 px, 8× ({name})", font=C.font(16), fill=(51, 65, 85), anchor="mm")
    tiles.append(label(notif_zoom, "Notification silhouettes at mdpi"))
    tiles.append(label(notification_row(r, True), "Ongoing notification, dark shade"))
    tiles.append(label(notification_row(r, False), "Ongoing notification, light shade"))
    row = Image.new("RGBA", (600, 420), (226, 230, 238, 255))
    row.alpha_composite(phone_splash(r, True), (30, 0))
    row.alpha_composite(phone_splash(r, False), (310, 0))
    tiles.append(label(row, "Phone splash (Android 12+), dark / light"))
    w = Image.new("RGBA", (600, 420), (226, 230, 238, 255))
    w.alpha_composite(watch_list(r), (120, 30))
    tiles.append(label(w, "Wear OS app list (48 dp icon, round mask)"))
    w2 = Image.new("RGBA", (600, 420), (226, 230, 238, 255))
    w2.alpha_composite(watch_splash(r), (120, 30))
    tiles.append(label(w2, "Watch splash (core-splashscreen, 90 dp icon)"))
    tp = Image.new("RGBA", (600, 420), (226, 230, 238, 255))
    tp.alpha_composite(Image.open(os.path.join(APP_RES, "drawable-round-nodpi", "tile_preview_dose.png")).resize((270, 270)), (20, 60))
    tp.alpha_composite(Image.open(os.path.join(APP_RES, "drawable-round-nodpi", "tile_preview_listening.png")).resize((270, 270)), (310, 60))
    tiles.append(label(tp, "Tile previews (round)"))
    cols = 2
    cw, chs = 640, []
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    heights = [max(t.height for t in rw) + 20 for rw in rows]
    sheet = Image.new("RGBA", (cw * cols + 20, sum(heights) + 80), (246, 247, 250, 255))
    ImageDraw.Draw(sheet).text((24, 40), "Voxara icon set — Gauge V (rendered from the shipped VectorDrawables)",
                               font=C.font(28, True), fill=(15, 23, 42), anchor="lm")
    y = 80
    for rw, h in zip(rows, heights):
        for j, t in enumerate(rw):
            sheet.alpha_composite(t, (20 + j * cw + (cw - 20 - t.width) // 2, y))
        y += h
    sheet.convert("RGB").save(os.path.join(FINAL, "preview.png"), optimize=True)


def main():
    write_sources()
    write_android()
    write_tile_previews()
    r = render_all()
    store_assets(r)
    preview_sheet(r)
    import shutil
    shutil.rmtree(TMP, ignore_errors=True)
    print("done")


if __name__ == "__main__":
    main()
