"""Builds one HTML page per picture and captures it with headless Edge.

The page loads the design system by file:// URLs (tokens.css, bundle.css, React, bundle.js) and the kit's page.css
and page.js. Under the picture the page paints a status square: green when page.js has finished, red on an error.
The capture is taken a little taller than the picture so that square is in it; no green square, no picture.
"""
import base64
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

from PIL import Image

from .model import StoreError

STORE = Path(__file__).resolve().parent.parent
DS = STORE / "ds"
VENDOR = STORE / "vendor"
KIT = STORE / "storekit"
PHONE_PHOTO = DS / "assets" / "Device" / "phone-3-white-back.webp"

NEEDED = [DS / "tokens.css", DS / "tokens.json", DS / "components" / "bundle.css", DS / "components" / "bundle.js",
          DS / "fonts" / "Doto.ttf", DS / "fonts" / "Geist.ttf", DS / "fonts" / "GeistMono.ttf", PHONE_PHOTO,
          VENDOR / "react.production.min.js", VENDOR / "react-dom.production.min.js", KIT / "page.css", KIT / "page.js"]

BROWSERS = [r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
            r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"]

STATUS_BAND = 80          # extra capture height under the picture for the status square (at y + 20, 40x40)
GREEN, RED = (0, 255, 0), (255, 0, 0)

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<link rel="stylesheet" href="{ds}/tokens.css">
<link rel="stylesheet" href="{ds}/components/bundle.css">
<link rel="stylesheet" href="{kit}/page.css">
<style>#n7k-pic {{ width: {width}px; height: {height}px; }} #n7k-status {{ top: {status_top}px; }}</style>
</head>
<body>
<div id="n7k-pic"></div>
<div id="n7k-status"></div>
<script src="{vendor}/react.production.min.js"></script>
<script src="{vendor}/react-dom.production.min.js"></script>
<script src="{ds}/components/bundle.js"></script>
<script>window.N7_PAGE = {data};</script>
<script src="{kit}/page.js"></script>
</body>
</html>
"""


def check_kit():
    gone = [str(p) for p in NEEDED if not p.is_file()]
    if gone:
        raise StoreError("the store kit is incomplete, missing:\n" + "\n".join("  " + p for p in gone))


def find_browser():
    override = os.environ.get("N7_STORE_BROWSER")
    for path in ([override] if override else BROWSERS):
        if path and Path(path).is_file():
            return Path(path)
    if override:
        raise StoreError(f"N7_STORE_BROWSER points to {override}, which does not exist")
    raise StoreError("Microsoft Edge not found (looked for msedge.exe and chrome.exe under Program Files). "
                     "Set N7_STORE_BROWSER to a Chromium-based browser.")


def url(path):
    return Path(path).resolve().as_uri()


class Browser:
    def __init__(self, work):
        """`work` is the temp folder for pages, raw captures and the browser profile (inside the app's store/out)."""
        check_kit()
        self.exe = find_browser()
        self.work = Path(work)
        if self.work.exists():
            shutil.rmtree(self.work)
        self.work.mkdir(parents=True)

    def shoot(self, name, data, width, height, scale=1):
        """Renders the page described by `data` and returns (RGB image width x height, the page's report)."""
        html = self.work / f"{name}.html"
        html.write_text(PAGE.format(
            title=name, ds=url(DS), vendor=url(VENDOR), kit=url(KIT), width=width, height=height,
            status_top=height + 20,
            data=json.dumps(data, ensure_ascii=True, separators=(",", ":")).replace("</", "<\\/")), encoding="utf-8")
        image, report = None, {}
        for budget in (4000, 15000):            # a slow machine gets one longer second try
            image, report = self._capture(name, html, width, height, scale, budget)
            status = image.getpixel((20 * scale, (height + 40) * scale))[:3]
            if status == GREEN:
                break
        else:
            reason = report.get("error") or ("the page reported an error" if status == RED else
                                             "the page did not finish in time (fonts or images still loading)")
            raise StoreError(f"{name}: {reason}\n  page: {html}")
        picture = image.crop((0, 0, width * scale, height * scale)).convert("RGB")
        if scale != 1:
            picture = picture.resize((width, height), Image.LANCZOS)
        return picture, report

    def _capture(self, name, html, width, height, scale, budget):
        raw = self.work / f"{name}.raw.png"
        raw.unlink(missing_ok=True)
        # --disable-lcd-text: greyscale text antialiasing. Subpixel (LCD) antialiasing fringes white text with pure red
        # and blue pixels, and red belongs to the signal alone.
        command = [str(self.exe), "--headless=new", "--disable-gpu", "--disable-lcd-text", "--hide-scrollbars",
                   f"--force-device-scale-factor={scale}", f"--window-size={width},{height + STATUS_BAND}",
                   f"--virtual-time-budget={budget}", f"--user-data-dir={self.work / 'profile'}",
                   "--no-first-run", "--no-default-browser-check", "--disable-extensions", "--disable-sync",
                   "--disable-background-networking", "--disable-component-update",
                   "--enable-logging=stderr", "--log-level=0", f"--screenshot={raw}", url(html)]
        try:
            done = subprocess.run(command, capture_output=True, timeout=180)
        except subprocess.TimeoutExpired:
            raise StoreError(f"{name}: the browser did not answer within 3 minutes ({self.exe})") from None
        except OSError as e:
            raise StoreError(f"{name}: cannot start {self.exe}: {e}") from None
        log = done.stderr.decode("utf-8", "replace")
        if not raw.is_file():
            tail = "\n".join(log.strip().splitlines()[-5:])
            raise StoreError(f"{name}: the browser wrote no screenshot (exit code {done.returncode}).\n{tail}")
        with Image.open(raw) as im:
            image = im.convert("RGB")
        expected = (width * scale, (height + STATUS_BAND) * scale)
        if image.size != expected:
            raise StoreError(f"{name}: the capture is {image.size[0]}x{image.size[1]}, expected {expected[0]}x{expected[1]}")
        return image, _report(log)

    def close(self, keep=False):
        if not keep:
            shutil.rmtree(self.work, ignore_errors=True)
        else:
            shutil.rmtree(self.work / "profile", ignore_errors=True)


def _report(log):
    """The page's console line "N7STORE:<base64 json>" from the browser log, or {} when the log has none."""
    found = re.findall(r"N7STORE:([A-Za-z0-9+/=]+)", log)
    if not found:
        return {}
    try:
        return json.loads(base64.b64decode(found[-1]).decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return {}
