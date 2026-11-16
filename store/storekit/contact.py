"""The contact sheet: every generated picture of an app on one PNG, for review. Built from the files on disk."""
from PIL import Image, ImageDraw, ImageFont

from . import drawables
from .browser import DS

GROUND = (28, 28, 28)        # surface-raised: the pictures are black, so the sheet is not
INK = (255, 255, 255)
SUBTLE = (140, 140, 140)
PAD = 24
SHOT = (360, 640)
PER_ROW = 5


def _font(size):
    return ImageFont.truetype(str(DS / "fonts" / "GeistMono.ttf"), size)


def raster(paths, size, background=(0, 0, 0)):
    """Draws a parsed vector drawable (108dp viewport, circles only) as a size x size RGB image."""
    k = 8
    big = Image.new("RGB", (drawables.VIEWPORT * k, drawables.VIEWPORT * k), background)
    d = ImageDraw.Draw(big)
    for p in paths:
        fill = p["fill"] if p["fill"] and p["fill"].upper() != "#00000000" else None
        for cx, cy, r in p["circles"]:
            if fill:
                d.ellipse(((cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k), fill=fill)
            if p["stroke"] and p["stroke_width"] > 0:
                o = r + p["stroke_width"] / 2
                d.ellipse(((cx - o) * k, (cy - o) * k, (cx + o) * k, (cy + o) * k), outline=p["stroke"],
                          width=max(1, round(p["stroke_width"] * k)))
    return big.resize((size, size), Image.LANCZOS)


def _launcher(paths, size, background=(0, 0, 0), round_mask=True):
    """How a launcher shows an adaptive icon: the middle 72dp of the 108dp layers, masked."""
    full = raster(paths, size * 3 // 2, background)
    edge = (full.width - size) // 2
    icon = full.crop((edge, edge, edge + size, edge + size))
    mask = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(mask)
    if round_mask:
        d.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 * 0.24, fill=255)
    return icon, mask.resize((size, size), Image.LANCZOS)


def build(store, paths, out):
    """`paths` = {"shots": [Path...], "feature": Path, "icon": Path, "foreground": Path, "monochrome": Path, "toys": [(name, Path)]}.
    Files that do not exist yet are drawn as empty frames."""
    label, small, title = _font(20), _font(16), _font(28)
    shots = paths["shots"]
    shot_rows = max(1, -(-len(shots) // PER_ROW))
    width = PAD + PER_ROW * (SHOT[0] + PAD)
    feature_size = (768, 375)
    height = 80 + shot_rows * (SHOT[1] + 40 + PAD) + feature_size[1] + 40 + PAD
    sheet = Image.new("RGB", (width, height), GROUND)
    d = ImageDraw.Draw(sheet)
    d.text((PAD, 26), f"{store.name.upper()}  /  STORE GRAPHICS", font=title, fill=INK)

    def place(path, box, caption, note=""):
        x, y, w, h = box
        if path is not None and path.is_file():
            with Image.open(path) as im:
                im = im.convert("RGB")
                note = note or f"{im.width}x{im.height}"
                sheet.paste(im.resize((w, h), Image.LANCZOS), (x, y))
        else:
            d.rectangle((x, y, x + w - 1, y + h - 1), outline=SUBTLE)
            note = "not rendered yet"
        d.text((x, y + h + 8), caption, font=label, fill=INK)
        d.text((x + d.textlength(caption + "  ", font=label), y + h + 11), note, font=small, fill=SUBTLE)

    y = 80
    for i, path in enumerate(shots):
        col, row = i % PER_ROW, i // PER_ROW
        layout = store.shots[i].layout if store.shots and i < len(store.shots) else ""
        place(path, (PAD + col * (SHOT[0] + PAD), y + row * (SHOT[1] + 40 + PAD), *SHOT), f"{i + 1} {layout.upper()}")
    y += shot_rows * (SHOT[1] + 40 + PAD)

    place(paths["feature"], (PAD, y, *feature_size), "FEATURE")
    x = PAD + feature_size[0] + PAD
    place(paths["icon"], (x, y, 375, 375), "PLAY ICON")
    x += 375 + PAD

    def vector(path):
        if path is None or not path.is_file():
            return None
        try:
            return drawables.parse(path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return None

    size = 164
    tiles = []
    fg, mono = vector(paths["foreground"]), vector(paths["monochrome"])
    if fg:
        tiles.append(("LAUNCHER", *_launcher(fg, size)))
        tiles.append(("SQUIRCLE", *_launcher(fg, size, round_mask=False)))
    if mono:
        tiles.append(("THEMED", *_launcher(mono, size, background=(42, 42, 42))))
    for name, path in paths["toys"]:
        toy = vector(path)
        if toy:
            tiles.append((name.upper(), raster(toy, size), None))
    tx, ty = x, y
    for caption, image, mask in tiles:
        if tx + size > width - PAD:
            tx, ty = x, ty + size + 40 + PAD // 2
        sheet.paste(image, (tx, ty), mask)
        d.text((tx, ty + size + 8), caption[:14], font=small, fill=INK)
        tx += size + PAD
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, "PNG", optimize=True)
    return out
