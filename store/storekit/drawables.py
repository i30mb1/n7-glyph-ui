"""Android vector drawables of the round Glyph Matrix: launcher foreground, launcher monochrome, toy preview.

Same structure as the files Glyph Pet ships (and AAPT compiles): a 108dp vector, one <path> for the lens disc and
one <path> per LED colour whose pathData is a run of circles. The geometry is `GlyphMatrix` from the design system.
"""
import re
import xml.etree.ElementTree as ET

from . import matrix

VIEWPORT = 108
CENTRE = VIEWPORT / 2
# Lens radius in dp. Launcher: the disc (diameter 62) stays inside the 66dp safe zone of an adaptive icon.
# Toy preview: the disc fills the picture the Glyph Toys list shows.
LAUNCHER_LENS = 31.0
TOY_LENS = 52.0
MONO_RIM = "0.60"

ANDROID = "http://schemas.android.com/apk/res/android"
HEADER = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp" android:height="108dp"\n'
          '    android:viewportWidth="108" android:viewportHeight="108">\n')


def _circle(cx, cy, r, digits):
    """A full circle as two arcs, the way Glyph Pet's drawables write it."""
    f = f"%.{digits}f"
    return (f"M{f % (cx - r)},{f % cy}a{f % r},{f % r} 0,1 0,{f % (2 * r)},0"
            f"a{f % r},{f % r} 0,1 0,{f % (-2 * r)},0z")


def _leds(cells, lens):
    scale = lens / matrix.LENS_R
    return "".join(_circle(CENTRE + (x + 0.5 - matrix.SIZE / 2) * scale, CENTRE + (y + 0.5 - matrix.SIZE / 2) * scale,
                           matrix.LED_R * scale, 3) for x, y in cells)


def _hex(rgb):
    return "#%02X%02X%02X" % tuple(rgb)


def _rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def led_colour(level, colours):
    """The colour `GlyphMatrix` gives a lit LED: from the unlit LED colour to full white, straight with the level."""
    on = _rgb(colours["led-on"])
    off = _rgb(colours["led-off"])
    level = max(0.0, min(1.0, level))
    return _hex(round(f * level + b * (1 - level)) for f, b in zip(on, off))


def _comment_text(text):
    """`text` made legal inside an XML comment: no "--" (any run of dashes becomes one) and no XML-illegal control
    characters. The comment is written with a space on each side, so a trailing dash cannot touch the "-->"."""
    text = _ILLEGAL_XML.sub("", text)
    return re.sub(r"-{2,}", "-", text)


_ILLEGAL_XML = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f\ud800-\udfff￾￿]")


def _document(comment, paths):
    comment = _comment_text(comment)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- {comment} -->\n" + HEADER + "".join(f"    {p}\n" for p in paths) + "</vector>\n")


def _lens_path(lens):
    return _circle(CENTRE, CENTRE, lens, 1)


def colour(cells, signal, colours, lens, comment):
    """Lens disc, unlit LEDs, lit LEDs (one path per brightness), then the red signal cells."""
    signal = [c for c in signal if matrix.visible(*c)]
    lit = {c: lv for c, lv in cells.items() if c not in signal}
    paths = [f'<path android:fillColor="{_hex(_rgb(colours["lens"]))}" android:strokeColor="{_hex(_rgb(colours["lens-rim"]))}" '
             f'android:strokeWidth="{matrix.RIM * lens / matrix.LENS_R:.3f}" android:pathData="{_lens_path(lens)}"/>']
    unlit = [c for c in matrix.visible_cells() if c not in lit and c not in signal]
    groups = [(_hex(_rgb(colours["led-off"])), unlit)]
    by_colour = {}
    for c in matrix.visible_cells():
        if c in lit:
            by_colour.setdefault(led_colour(lit[c], colours), []).append(c)
    groups += sorted(by_colour.items())                       # darker first, white last
    groups.append((_hex(_rgb(colours["signal"])), signal))
    for fill, group in groups:
        if group:
            paths.append(f'<path android:fillColor="{fill}" android:pathData="{_leds(group, lens)}"/>')
    return _document(comment, paths)


def monochrome(cells, signal, lens, comment):
    """The same picture in one colour for themed icons: the lens ring and the lit LEDs (signal included)."""
    lit = [c for c in matrix.visible_cells() if c in cells or c in signal]
    paths = [f'<path android:strokeColor="#FFFFFF" android:strokeWidth="{MONO_RIM}" android:fillColor="#00000000" '
             f'android:pathData="{_lens_path(lens)}"/>']
    if lit:
        paths.append(f'<path android:fillColor="#FFFFFF" android:pathData="{_leds(lit, lens)}"/>')
    return _document(comment, paths)


# ---------- reading a drawable back (contact sheet, self-check) ----------

_CIRCLE = re.compile(r"M(-?[\d.]+),(-?[\d.]+)a([\d.]+),([\d.]+) 0,1 0,(-?[\d.]+),0a([\d.]+),([\d.]+) 0,1 0,(-?[\d.]+),0z")


def parse(text):
    """A generated drawable as a list of paths: {"fill", "stroke", "stroke_width", "circles": [(cx, cy, r), ...]}.
    Raises ValueError when the text is not a vector of circle paths."""
    root = ET.fromstring(text.encode("utf-8"))
    if root.tag != "vector":
        raise ValueError(f"root element is <{root.tag}>, not <vector>")
    a = "{%s}" % ANDROID
    out = []
    for el in root:
        if el.tag != "path":
            raise ValueError(f"unexpected element <{el.tag}>")
        data = el.get(a + "pathData", "")
        if _CIRCLE.sub("", data):
            raise ValueError("pathData is not a run of circles")
        circles = [(float(m[0]) + float(m[2]), float(m[1]), float(m[2])) for m in _CIRCLE.findall(data)]
        out.append({"fill": el.get(a + "fillColor"), "stroke": el.get(a + "strokeColor"),
                    "stroke_width": float(el.get(a + "strokeWidth", "0")), "circles": circles})
    return out


def root_attributes(text):
    root = ET.fromstring(text.encode("utf-8"))
    a = "{%s}" % ANDROID
    return {k.replace(a, "android:"): v for k, v in root.attrib.items()}
