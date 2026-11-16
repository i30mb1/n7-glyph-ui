"""Tests of the store kit. Run from the store folder:  python -m unittest discover -s tests -v

The render tests start headless Edge (skipped when no Chromium browser is found) and use inline cells, so they need no
frames.json and no app project beyond a temporary store/store.json.
"""
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

STORE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(STORE))

from PIL import Image  # noqa: E402

from storekit import browser, drawables  # noqa: E402
from storekit.model import StoreError  # noqa: E402

RENDER = STORE / "render.py"
GRAPHICS = Path("app/src/main/play/listings/en-US/graphics")
TOKENS = {t["name"]: t["value"] for t in json.loads((STORE / "ds" / "tokens.json").read_text(encoding="utf-8"))["color"]["tokens"]}

DOT = [[x, y] for x in range(10, 15) for y in range(10, 15)]
LONG = ("Hold the Glyph button to strike a match, tilt the phone to make the flame lean, and shake it to blow it out "
        "before the battery runs out of patience.")


def has_browser():
    try:
        browser.find_browser()
        return True
    except StoreError:
        return False


def render(store, *only):
    """Writes store.json into a fresh temporary app project and runs render.py on it. Returns (result, app path, tmp)."""
    tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
    app = Path(tmp.name)
    (app / "store").mkdir()
    (app / "store" / "store.json").write_text(json.dumps(store), encoding="utf-8")
    args = [sys.executable, str(RENDER), str(app)]
    for section in only:
        args += ["--only", section]
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600)
    return result, app, tmp


def written(app):
    src = app / "app" / "src"
    return [p for p in src.rglob("*") if p.is_file()] if src.exists() else []


class DrawableCommentTest(unittest.TestCase):
    """The app name goes into an XML comment, where "--" and control characters are illegal."""

    NAMES = ["Glyph --- Pet", "Glyph -- Pet", "A----B", "Pet-", "-", "x\x07y", "Bell\x00"]

    def check(self, text):
        drawables.parse(text)                                  # reads back as a vector of circle paths
        body = re.search(r"<!--(.*?)-->", text, re.S).group(1)
        self.assertNotIn("--", body)
        self.assertFalse(body.endswith("-"))

    def test_colour_and_monochrome_with_awkward_names(self):
        for name in self.NAMES:
            with self.subTest(name=name):
                self.check(drawables.colour({(12, 12): 1.0}, [(11, 11)], TOKENS, drawables.LAUNCHER_LENS, f"{name}: icon"))
                self.check(drawables.monochrome({(12, 12): 1.0}, [], drawables.LAUNCHER_LENS, f"{name}: icon"))

    def test_render_drawables_with_triple_dash_name(self):
        result, app, tmp = render({"name": "Glyph --- Pet", "icon": {"cells": DOT, "signal": [[12, 9]]},
                                   "toys": [{"drawable": "toy_preview"}]}, "drawables")
        with tmp:
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("Traceback", result.stderr)
            text = (app / "app/src/main/res/drawable/ic_launcher_foreground.xml").read_text(encoding="utf-8")
            self.assertIn("<!-- Glyph - Pet: launcher icon", text)


@unittest.skipUnless(has_browser(), "no Chromium browser for the page tests")
class PageTest(unittest.TestCase):

    def test_steps_that_run_off_the_picture_fail_and_write_nothing(self):
        result, app, tmp = render({"name": "T", "shots": [{
            "layout": "steps", "headline": ["LIGHT", "IT", "UP"], "sub": LONG, "cells": DOT,
            "steps": ["Open Glyph Toys in the Nothing settings app on your phone", "Add Glyph Awkward to the list of active toys",
                      "Press the Glyph button on the back until the flame appears", "Hold it to light the flame"]}]}, "shots")
        with tmp:
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("error: shot-1: the steps do not fit under the text", result.stderr)
            self.assertNotIn("Traceback", result.stderr)
            self.assertEqual(written(app), [])

    def test_feature_text_that_runs_off_the_picture_fails_and_writes_nothing(self):
        result, app, tmp = render({"name": "T", "feature": {
            "title": ["GLYPH", "AWKWARD", "FLAME"], "sub": LONG + " " + LONG, "cells": DOT}}, "feature")
        with tmp:
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("error: feature: the feature text does not fit in the 500px picture", result.stderr)
            self.assertEqual(written(app), [])

    def test_text_under_the_headline_has_no_red_pixels(self):
        """White text must be antialiased in grey: colour (LCD) antialiasing puts pure red fringes on it, and red is
        the signal alone. Below the text block these layouts have no red element at all."""
        result, app, tmp = render({"name": "T", "shots": [
            {"layout": "steps", "headline": ["TURN", "IT ON"], "sub": "Add it to your Glyph Toys.", "cells": DOT,
             "steps": ["Open Glyph Toys", "Add Glyph Flame", "Press the Glyph button"]},
            {"layout": "grid", "headline": ["ITS", "MOODS"], "sub": "Calm, curious, hungry.", "frames": [DOT, DOT, DOT],
             "labels": ["REST", "ROLL", "CHARGE"]}]}, "shots")
        with tmp:
            self.assertEqual(result.returncode, 0, result.stderr)
            for n in (1, 2):
                with Image.open(app / GRAPHICS / "phone-screenshots" / f"{n}.png") as im:
                    im = im.convert("RGB")
                    below = im.crop((0, 720, im.width, im.height))      # under a two-line headline + one sub line
                    data = below.tobytes()
                    red = [i for i in range(0, len(data), 3)
                           if data[i] > 140 and data[i + 1] < 90 and data[i + 2] < 90 and data[i] - data[i + 1] > 80]
                self.assertEqual(len(red), 0, f"shot {n}: {len(red)} red pixels under the text block")


if __name__ == "__main__":
    unittest.main()
