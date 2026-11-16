"""The Google Play icon (512x512): `AppIcon` from the design system, shape square, fill 0.82, no glow.

Drawn here with Pillow rather than captured from the browser: four times supersampled ellipses scaled down with Lanczos.
This is how Glyph Pet's Play icon was made (this code reproduces it pixel for pixel); a browser capture of `AppIcon` differs
by up to 80 levels along the lens rim. Geometry and colours are `GlyphMatrix`'s: a 28.2-cell view box, lens radius 13.85
with a 0.22 rim, LED radius 0.38, a lit LED mixed from the unlit colour to white by its level, signal cells in red.
"""
from PIL import Image, ImageDraw

from . import drawables, matrix

SUPERSAMPLE = 4
VIEWBOX = 28.2


def play_icon(cells, signal, colours, size=512, fill=0.82):
    """`cells` {(x, y): level}, `signal` [(x, y)], `colours` the design-system colour tokens. Returns an RGB image."""
    k = SUPERSAMPLE
    big = size * k
    unit = size * fill / VIEWBOX * k            # pixels per cell at the supersampled scale
    c = big / 2
    rgb = {name: drawables._rgb(colours[name]) for name in ("ground", "lens", "lens-rim", "led-off", "signal")}
    img = Image.new("RGB", (big, big), rgb["ground"])
    d = ImageDraw.Draw(img)
    radius = matrix.LENS_R * unit
    d.ellipse((c - radius, c - radius, c + radius, c + radius), fill=rgb["lens"], outline=rgb["lens-rim"],
              width=max(1, round(matrix.RIM * unit)))
    signal = set(signal)
    r = matrix.LED_R * unit
    for x, y in matrix.visible_cells():
        if (x, y) in signal:
            colour = rgb["signal"]
        elif (x, y) in cells:
            colour = drawables._rgb(drawables.led_colour(cells[(x, y)], colours))
        else:
            colour = rgb["led-off"]
        cx, cy = c + (x - 12) * unit, c + (y - 12) * unit
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour)
    return img.resize((size, size), Image.LANCZOS)
