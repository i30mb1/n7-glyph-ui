"""Reads and checks `<app>/store/store.json` and the engine frames in `<app>/app/build/store/frames.json`.

Everything a builder can get wrong is reported here, with the place in the file, before a browser starts.
"""
import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from . import fonts, matrix

LAYOUTS = ("phone", "zoom", "grid", "screen", "steps")
SECTIONS = ("shots", "feature", "icon", "drawables")
CYRILLIC = re.compile("[Ѐ-ӿ]")
RESOURCE_NAME = re.compile(r"^[a-z][a-z0-9_]*$")
RESERVED_DRAWABLES = ("ic_launcher_foreground", "ic_launcher_monochrome")

TOP_KEYS = {"name", "icon", "toys", "feature", "shots"}
ICON_KEYS = {"frame", "cells", "signal"}
TOY_KEYS = {"drawable", "frame", "cells", "signal"}
FEATURE_KEYS = {"title", "sub", "frame", "cells"}
SHOT_KEYS = {
    "phone": {"frame", "cells"},
    "zoom": {"frame", "cells"},
    "grid": {"frames", "labels", "dim"},
    "screen": {"image"},
    "steps": {"steps", "frame", "cells"},
}


class StoreError(Exception):
    """Something the builder can fix: a wrong store.json, a missing file, no browser."""


class MissingFrames(StoreError):
    """A picture names an engine frame but app/build/store/frames.json does not exist. `where` is the picture's place."""

    def __init__(self, where):
        super().__init__(where)
        self.where = where


def missing_frames_message(path, places):
    return (f"{path} does not exist, and these pictures name frames from it: {', '.join(places)}. "
            "Run the app's unit tests first (StoreFramesTest writes it), or give the pictures inline as \"cells\".")


@dataclass
class Picture:
    """What a matrix shows: a named engine frame or inline cells, plus the red signal cells (icons only)."""
    where: str
    frame: str | None = None
    cells: dict | None = None
    signal: list = field(default_factory=list)


@dataclass
class Toy:
    drawable: str
    picture: Picture


@dataclass
class Feature:
    title: list
    sub: str
    picture: Picture


@dataclass
class Shot:
    where: str
    layout: str
    headline: list
    sub: str
    picture: Picture | None = None                  # phone, zoom, steps
    items: list = field(default_factory=list)       # grid: (Picture, label, dim)
    image: str | None = None                        # screen: path relative to the app project
    steps: list = field(default_factory=list)       # steps


@dataclass
class Store:
    app: Path
    name: str
    icon: Picture | None
    toys: list | None
    feature: Feature | None
    shots: list | None
    warnings: list


class _Check:
    """Collects every problem instead of stopping at the first one."""

    def __init__(self, ds_fonts):
        self.errors = []
        self.warnings = []
        self.fonts = ds_fonts

    def error(self, where, text):
        self.errors.append(f"{where}: {text}")

    def warn(self, where, text):
        self.warnings.append(f"{where}: {text}")

    # ----- small typed readers -----
    def keys(self, obj, allowed, where):
        for key in obj:
            if key not in allowed and not key.startswith("_"):
                hint = " (red belongs to the icon only: the hardware is white-only)" if key == "signal" else ""
                self.error(where, f'unknown key "{key}"{hint}; allowed: {", ".join(sorted(allowed))}')

    def text(self, obj, key, where, required=True):
        value = obj.get(key)
        if value is None:
            if required:
                self.error(where, f'"{key}" is required')
            return ""
        if not isinstance(value, str) or (required and not value.strip()):
            self.error(f"{where}.{key}", "must be a non-empty string")
            return ""
        return value.strip()

    def lines(self, obj, key, where, most=3):
        value = obj.get(key)
        if not isinstance(value, list) or not value or not all(isinstance(v, str) and v.strip() for v in value):
            self.error(f"{where}.{key}", f"must be a list of 1 to {most} non-empty strings, one per line")
            return []
        if len(value) > most:
            self.error(f"{where}.{key}", f"{len(value)} lines; the house headline has 1 to {most}")
        lines = [v.strip() for v in value]
        # The dot type has no Cyrillic: DotHeadline sets a headline with Cyrillic in Geist. Either way every letter must exist.
        font, family = ("Geist.ttf", "Geist") if any(CYRILLIC.search(v) for v in lines) else ("Doto.ttf", "Doto, the dot type")
        gone = fonts.missing("".join(lines), self.fonts / font)
        if gone:
            self.error(f"{where}.{key}", f"{_chars(gone)} not in {family}; the browser would fall back to another font")
        return lines

    def body(self, value, where, font="Geist.ttf"):
        gone = fonts.missing(value, self.fonts / font)
        if gone:
            self.warn(where, f"{_chars(gone)} not in {font[:-4]}; it will be drawn in a fallback font")

    def cells(self, value, where):
        if not isinstance(value, list):
            self.error(where, "must be a list of [x, y] or [x, y, level]")
            return {}
        out, outside = {}, 0
        for i, cell in enumerate(value):
            ok = isinstance(cell, list) and len(cell) in (2, 3) and all(_is_int(v) for v in cell[:2]) \
                and (len(cell) == 2 or _is_number(cell[2]))
            if not ok:
                self.error(f"{where}[{i}]", f"{json.dumps(cell)} is not [x, y] or [x, y, level]")
                continue
            x, y = cell[0], cell[1]
            lv = 1.0 if len(cell) == 2 else float(cell[2])
            if not (0 <= x < matrix.SIZE and 0 <= y < matrix.SIZE):
                self.error(f"{where}[{i}]", f"({x}, {y}) is off the 25x25 matrix (0..24)")
                continue
            if not 0 <= lv <= 1:
                self.error(f"{where}[{i}]", f"level {cell[2]} is not within 0..1 (1 = full white)")
                continue
            if not matrix.visible(x, y):
                outside += 1
                continue
            if lv > 0:
                out[(x, y)] = lv
        if outside:
            self.warn(where, f"{outside} cell(s) lie outside the round matrix and are not drawn")
        return out

    def picture(self, obj, where, signal=False, default=None):
        """Reads "frame" (a name in frames.json) or inline "cells"; with signal=True also the red "signal" cells."""
        frame, cells = obj.get("frame"), obj.get("cells")
        pic = Picture(where)
        if frame is not None and cells is not None:
            self.error(where, 'give "frame" or "cells", not both')
        elif frame is not None:
            if isinstance(frame, str) and frame:
                pic.frame = frame
            else:
                self.error(f"{where}.frame", "must be the name of a frame in app/build/store/frames.json")
        elif cells is not None:
            pic.cells = self.cells(cells, f"{where}.cells")
        elif default is not None:
            pic.frame, pic.cells = default.frame, default.cells
        else:
            self.error(where, 'needs "frame" (a name in app/build/store/frames.json) or inline "cells"')
        if signal and obj.get("signal") is not None:
            pic.signal = sorted(self.cells(obj["signal"], f"{where}.signal"), key=lambda c: (c[1], c[0]))
        return pic


def _is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def _is_number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def _chars(chars):
    return ", ".join(f'"{c}" (U+{ord(c):04X})' for c in chars)


def load(app, ds_fonts):
    """Parses store.json. Raises StoreError listing every problem found."""
    path = app / "store" / "store.json"
    if not path.is_file():
        raise StoreError(f"{path} not found. The store kit reads its input from <app-project>/store/store.json.")
    try:
        raw = json.loads(path.read_text(encoding="utf-8-sig"))
    except (json.JSONDecodeError, UnicodeDecodeError) as e:
        raise StoreError(f"{path}: not valid JSON: {e}") from None
    if not isinstance(raw, dict):
        raise StoreError(f"{path}: the top level must be an object")

    c = _Check(ds_fonts)
    c.keys(raw, TOP_KEYS, "store.json")
    name = c.text(raw, "name", "store.json")

    icon = None
    if "icon" in raw:
        if isinstance(raw["icon"], dict):
            c.keys(raw["icon"], ICON_KEYS, "icon")
            icon = c.picture(raw["icon"], "icon", signal=True)
            if not icon.signal:
                c.warn("icon", 'no "signal": a family icon has exactly one red element')
        else:
            c.error("icon", "must be an object")

    toys = None
    if "toys" in raw:
        toys = []
        if not isinstance(raw["toys"], list):
            c.error("toys", "must be a list")
        else:
            for i, t in enumerate(raw["toys"]):
                where = f"toys[{i}]"
                if not isinstance(t, dict):
                    c.error(where, "must be an object")
                    continue
                c.keys(t, TOY_KEYS, where)
                drawable = c.text(t, "drawable", where)
                if drawable and not RESOURCE_NAME.match(drawable):
                    c.error(f"{where}.drawable", f'"{drawable}" is not an Android resource name (a-z, 0-9, _)')
                elif drawable in RESERVED_DRAWABLES:
                    c.error(f"{where}.drawable", f'"{drawable}" is the launcher icon; pick another name')
                elif drawable in [toy.drawable for toy in toys]:
                    c.error(f"{where}.drawable", f'"{drawable}" is used twice')
                pic = c.picture(t, where, signal=True, default=icon)
                # A toy that shows the icon's picture keeps the icon's red element unless it sets its own.
                if icon and "signal" not in t and (pic.frame, pic.cells) == (icon.frame, icon.cells):
                    pic.signal = list(icon.signal)
                toys.append(Toy(drawable, pic))

    feature = None
    if "feature" in raw:
        f = raw["feature"]
        if isinstance(f, dict):
            c.keys(f, FEATURE_KEYS, "feature")
            sub = c.text(f, "sub", "feature", required=False)
            c.body(sub, "feature.sub")
            feature = Feature(c.lines(f, "title", "feature"), sub, c.picture(f, "feature"))
        else:
            c.error("feature", "must be an object")

    shots = None
    if "shots" in raw:
        shots = []
        if not isinstance(raw["shots"], list) or not raw["shots"]:
            c.error("shots", "must be a non-empty list")
        else:
            if len(raw["shots"]) > 8:
                c.error("shots", f"{len(raw['shots'])} screenshots; Google Play takes 8 at most")
            elif len(raw["shots"]) < 2:
                c.warn("shots", "Google Play needs at least 2 phone screenshots")
            for i, s in enumerate(raw["shots"]):
                shot = _shot(c, s, f"shots[{i}]")
                if shot:
                    shots.append(shot)
            if shots and shots[0].layout != "phone":
                c.warn("shots[0]", 'the first screenshot of every app is the "phone" layout')

    if c.errors:
        raise StoreError(f"{path} has {len(c.errors)} problem(s):\n" + "\n".join("  " + e for e in c.errors))
    return Store(app, name, icon, toys, feature, shots, c.warnings)


def _shot(c, s, where):
    if not isinstance(s, dict):
        c.error(where, "must be an object")
        return None
    layout = s.get("layout")
    if layout not in LAYOUTS:
        c.error(f"{where}.layout", f'{json.dumps(layout)} is not a layout; use one of: {", ".join(LAYOUTS)}')
        return None
    c.keys(s, {"layout", "headline", "sub"} | SHOT_KEYS[layout], where)
    sub = c.text(s, "sub", where, required=False)
    c.body(sub, f"{where}.sub")
    shot = Shot(where, layout, c.lines(s, "headline", where), sub)

    if layout in ("phone", "zoom", "steps"):
        shot.picture = c.picture(s, where)
    if layout == "grid":
        frames = s.get("frames")
        if not isinstance(frames, list) or not 2 <= len(frames) <= 9:
            c.error(f"{where}.frames", "must be a list of 2 to 9 frames (names, or inline cell lists)")
            return shot
        labels = s.get("labels")
        if labels is not None and (not isinstance(labels, list) or len(labels) != len(frames)
                                   or not all(isinstance(v, str) for v in labels)):
            c.error(f"{where}.labels", f"must be {len(frames)} strings, one per frame (\"\" for no label)")
            labels = None
        dim = s.get("dim", [])
        if dim is True:
            dim = list(range(len(frames)))
        if not isinstance(dim, list) or not all(_is_int(v) and 0 <= v < len(frames) for v in dim):
            c.error(f"{where}.dim", f"must be true or a list of frame indexes 0..{len(frames) - 1} whose labels are muted")
            dim = []
        for i, fr in enumerate(frames):
            w = f"{where}.frames[{i}]"
            if isinstance(fr, str) and fr:
                pic = Picture(w, frame=fr)
            elif isinstance(fr, list):
                pic = Picture(w, cells=c.cells(fr, w))
            elif isinstance(fr, dict):
                c.keys(fr, {"frame", "cells"}, w)
                pic = c.picture(fr, w)
            else:
                c.error(w, "must be a frame name or an inline list of cells")
                continue
            label = labels[i].strip() if labels else ""
            c.body(label.upper(), f"{where}.labels[{i}]", "GeistMono.ttf")
            shot.items.append((pic, label, i in dim))
    if layout == "screen":
        shot.image = c.text(s, "image", where)
    if layout == "steps":
        steps = s.get("steps")
        if not isinstance(steps, list) or not 1 <= len(steps) <= 4 or not all(isinstance(v, str) and v.strip() for v in steps):
            c.error(f"{where}.steps", "must be a list of 1 to 4 non-empty strings (the house shot has three)")
        else:
            shot.steps = [v.strip() for v in steps]
            for i, v in enumerate(shot.steps):
                c.body(v, f"{where}.steps[{i}]")
    return shot


class Frames:
    """The named frames written by the app's StoreFramesTest with the real engine. Read on first use."""

    def __init__(self, app):
        self.path = app / "app" / "build" / "store" / "frames.json"
        self._frames = None

    def _load(self, where, name):
        if not self.path.is_file():
            raise MissingFrames(where)
        try:
            data = json.loads(self.path.read_text(encoding="utf-8-sig"))
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            raise StoreError(f"{self.path}: not valid JSON: {e}") from None
        if not isinstance(data, dict):
            raise StoreError(f'{self.path}: expected an object {{"name": [625 numbers], ...}}')
        return data

    def cells(self, name, where):
        if self._frames is None:
            self._frames = self._load(where, name)
        if name not in self._frames:
            known = ", ".join(sorted(self._frames)) or "none"
            raise StoreError(f'{where}: no frame "{name}" in {self.path} (it has: {known})')
        values = self._frames[name]
        count = matrix.SIZE * matrix.SIZE
        if not isinstance(values, list) or len(values) != count or not all(_is_int(v) and 0 <= v <= matrix.MAX for v in values):
            raise StoreError(f'{self.path}: frame "{name}" must be {count} whole numbers 0..{matrix.MAX}, row by row')
        return matrix.cells_from_frame(values)


def resolve(picture, frames):
    """The lit cells {(x, y): level} of a Picture."""
    if picture.cells is not None:
        return picture.cells
    return frames.cells(picture.frame, picture.where)
