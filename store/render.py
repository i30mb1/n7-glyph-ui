"""Store graphics for an n7 Glyph app, in the family look: one command for all of them.

Usage:   python render.py <app-project-dir> [--only shots|feature|icon|drawables] [--keep]

Reads    <app>/store/store.json                    what to draw (see README.md next to this file)
         <app>/app/build/store/frames.json         named frames, written by the app's StoreFramesTest with the real engine
Writes   app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png ...   1080x1920
         app/src/main/play/listings/en-US/graphics/feature-graphic/1.png        1024x500
         app/src/main/play/listings/en-US/graphics/icon/1.png                   512x512
         app/src/main/ic_launcher-playstore.png                                 512x512
         app/src/main/res/drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml, <toy drawable>.xml
         store/out/contact.png                     everything on one sheet, for review

The PNGs are pages built from the N7 Glyph design system (store/ds) and captured with headless Edge;
the vector drawables are written directly. Nothing is written unless every picture of the run succeeded.
Needs Python 3 with Pillow and Microsoft Edge (or set N7_STORE_BROWSER to another Chromium browser).
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

from storekit import browser, contact, drawables, icon, matrix, model
from storekit.browser import DS, PHONE_PHOTO, url
from storekit.model import SECTIONS, StoreError

GRAPHICS = Path("app/src/main/play/listings/en-US/graphics")
RES_DRAWABLE = Path("app/src/main/res/drawable")
PLAYSTORE_ICON = Path("app/src/main/ic_launcher-playstore.png")
SHOT_SIZE, FEATURE_SIZE, ICON_SIZE = (1080, 1920), (1024, 500), 512
ICON_FILL = 0.82          # AppIcon fill for the Play icon; the launcher drawables use their own lens radius


def colours():
    tokens = json.loads((DS / "tokens.json").read_text(encoding="utf-8"))
    return {t["name"]: t["value"] for t in tokens["color"]["tokens"]}


def plan(store, sections, frames):
    """Everything this run will make: browser pages, pictures drawn in Python, drawable texts. Every picture is resolved
    here, so a missing frame or screenshot stops the run before anything is rendered. Returns (pages, drawn, xmls)."""
    app, problems, no_frames = store.app, [], []
    pages, drawn, xmls = [], [], []

    def cells(picture):
        try:
            return model.resolve(picture, frames)
        except model.MissingFrames as e:
            if e.where not in no_frames:
                no_frames.append(e.where)
            return {}
        except StoreError as e:
            problems.append(str(e))
            return {}

    for section in sections:
        if getattr(store, {"shots": "shots", "feature": "feature", "icon": "icon", "drawables": "icon"}[section]) is None:
            key = "icon" if section == "drawables" else section
            problems.append(f'store.json: "{key}" is required for {section}')
        elif section == "drawables" and store.toys is None:
            problems.append('store.json: "toys" is required for drawables (use [] when the app has no toy picture)')
    if problems:
        raise StoreError("\n".join(problems))

    phone = url(PHONE_PHOTO)
    if "shots" in sections:
        for i, shot in enumerate(store.shots):
            page = {"kind": "shot", "layout": shot.layout, "headline": shot.headline, "sub": shot.sub}
            if shot.picture is not None:
                page["cells"] = matrix.cell_list(cells(shot.picture))
            if shot.layout == "phone":
                page.update(phone=phone, preload=[phone])
            elif shot.layout == "grid":
                page["items"] = [{"cells": matrix.cell_list(cells(pic)), "label": label, "dim": dim}
                                 for pic, label, dim in shot.items]
            elif shot.layout == "screen":
                image = (app / shot.image).resolve()
                size = None
                if not image.is_file():
                    problems.append(f"{shot.where}.image: {image} not found. Take the screenshot of the running app "
                                    "(a PNG from the emulator) and save it there, then render again.")
                else:
                    try:
                        with Image.open(image) as im:
                            size = im.size
                    except OSError as e:
                        problems.append(f"{shot.where}.image: {image} is not a picture Pillow can read ({e})")
                if size:
                    page.update(image=url(image), imageSize=list(size), preload=[url(image)])
            elif shot.layout == "steps":
                page["steps"] = shot.steps
            pages.append((f"shot-{i + 1}", page, SHOT_SIZE, 1, [GRAPHICS / "phone-screenshots" / f"{i + 1}.png"]))

    if "feature" in sections:
        f = store.feature
        page = {"kind": "feature", "title": f.title, "sub": f.sub, "cells": matrix.cell_list(cells(f.picture)),
                "phone": phone, "preload": [phone]}
        pages.append(("feature", page, FEATURE_SIZE, 1, [GRAPHICS / "feature-graphic" / "1.png"]))

    icon_cells = cells(store.icon) if store.icon and ("icon" in sections or "drawables" in sections) else {}
    tokens = colours()
    if "icon" in sections:
        drawn.append(("icon", lambda: icon.play_icon(icon_cells, store.icon.signal, tokens, ICON_SIZE, ICON_FILL),
                      [GRAPHICS / "icon" / "1.png", PLAYSTORE_ICON]))

    if "drawables" in sections:
        name = store.name
        note = "Generated by n7-glyph-ui/store/render.py from store/store.json. Do not edit: change store.json and render again."
        xmls.append((RES_DRAWABLE / "ic_launcher_foreground.xml", drawables.colour(
            icon_cells, store.icon.signal, tokens, drawables.LAUNCHER_LENS, f"{name}: launcher icon, foreground. {note}")))
        xmls.append((RES_DRAWABLE / "ic_launcher_monochrome.xml", drawables.monochrome(
            icon_cells, store.icon.signal, drawables.LAUNCHER_LENS, f"{name}: launcher icon, monochrome. {note}")))
        for toy in store.toys:
            xmls.append((RES_DRAWABLE / f"{toy.drawable}.xml", drawables.colour(
                cells(toy.picture), toy.picture.signal, tokens, drawables.TOY_LENS,
                f"{name}: picture of the toy in the Glyph Toys list. {note}")))
        for path, text in xmls:        # our own output must read back as a vector of circle paths
            try:
                drawables.parse(text)
            except (ET.ParseError, ValueError, UnicodeError) as e:
                problems.append(f"{path.as_posix()}: the generated drawable does not read back ({e}); "
                                "this is a store kit bug, nothing was written")

    if no_frames:
        problems.insert(0, model.missing_frames_message(frames.path, no_frames))
    if problems:
        raise StoreError("\n".join(problems))
    return pages, drawn, xmls


def run(args):
    app = Path(args.app).resolve()
    if not app.is_dir():
        raise StoreError(f"{app} is not a directory. Give the app project folder (the one with store/store.json).")
    sections = [s for s in SECTIONS if s in args.only] if args.only else list(SECTIONS)
    browser.check_kit()
    store = model.load(app, DS / "fonts")
    pages, drawn, xmls = plan(store, sections, model.Frames(app))
    warnings = list(store.warnings)

    out = app / "store" / "out"
    out.mkdir(parents=True, exist_ok=True)
    (out / ".gitignore").write_text("*\n", encoding="utf-8")      # review output, never committed

    pictures = []
    if pages:
        edge = browser.Browser(out / "tmp")
        try:
            for name, page, (width, height), scale, targets in pages:
                print(f"rendering {name} ...", flush=True)
                picture, report = edge.shoot(name, page, width, height, scale)
                warnings += [f"{name}: {w}" for w in report.get("warnings", [])]
                if args.verbose:
                    print(f"  metrics {json.dumps(report.get('metrics', {}), separators=(',', ':'))}", flush=True)
                pictures.append((picture, targets))
        except StoreError:
            edge.close(keep=True)      # the page stays for a look in a browser
            raise
        edge.close(keep=args.keep)
    for name, draw, targets in drawn:
        print(f"drawing {name} ...", flush=True)
        pictures.append((draw(), targets))

    written = []
    for picture, targets in pictures:
        for target in targets:
            path = app / target
            path.parent.mkdir(parents=True, exist_ok=True)
            picture.save(path, "PNG", optimize=True)
            written.append(target)
    for target, text in xmls:
        path = app / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        written.append(target)
    if "shots" in sections:
        folder = app / GRAPHICS / "phone-screenshots"
        for old in folder.glob("*.png"):       # a screenshot dropped from store.json must not stay in the listing
            if re.fullmatch(r"\d+", old.stem) and int(old.stem) > len(store.shots):
                old.unlink()
                print(f"removed {(GRAPHICS / 'phone-screenshots' / old.name).as_posix()} (no longer in store.json)")

    shots = store.shots or []
    sheet = contact.build(store, {
        "shots": [app / GRAPHICS / "phone-screenshots" / f"{i + 1}.png" for i in range(len(shots))],
        "feature": app / GRAPHICS / "feature-graphic" / "1.png",
        "icon": app / GRAPHICS / "icon" / "1.png",
        "foreground": app / RES_DRAWABLE / "ic_launcher_foreground.xml",
        "monochrome": app / RES_DRAWABLE / "ic_launcher_monochrome.xml",
        "toys": [(t.drawable, app / RES_DRAWABLE / f"{t.drawable}.xml") for t in (store.toys or [])],
    }, out / "contact.png")

    for target in written:
        print(f"wrote {target.as_posix()}")
    print(f"wrote {sheet.relative_to(app).as_posix()}")
    if "drawables" in sections and not (app / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").is_file():
        print("note: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml is missing; the app template has the adaptive icon "
              "that points to ic_launcher_foreground and ic_launcher_monochrome")
    for w in warnings:
        print(f"warning: {w}")
    return 0


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(errors="backslashreplace")      # a Windows console code page must not break a message
    parser = argparse.ArgumentParser(description="Render the Google Play graphics and launcher drawables of an n7 Glyph app.")
    parser.add_argument("app", help="the app project folder (with store/store.json)")
    parser.add_argument("--only", action="append", choices=SECTIONS, default=[],
                        help="render only this part; may be given more than once")
    parser.add_argument("--keep", action="store_true", help="keep the HTML pages in store/out/tmp")
    parser.add_argument("--verbose", action="store_true", help="print where each page placed its parts (layout metrics)")
    args = parser.parse_args(argv)
    try:
        return run(args)
    except StoreError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
