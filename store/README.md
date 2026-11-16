# n7 store kit

One command renders every Google Play picture and launcher drawable of an n7 Glyph app in the family look
(rules of the look: `ds/README.md`).

```
python D:\AndroidProject\n7-glyph-ui\store\render.py <app-project-dir> [--only shots|feature|icon|drawables]... [--keep] [--verbose]
```

- `--only` may be repeated; without it all four parts are made.
- `--keep` keeps the generated HTML pages in `<app>/store/out/tmp` (they are always kept when a page fails).
- `--verbose` prints where each page placed its parts.
- Exit code 0 on success, 1 with an `error:` message on stderr otherwise. Nothing in the app is written unless every
  picture of the run succeeded. Text that would be cut off (steps under the text, the feature text) is an error that
  says what to shorten, never a warning. Same input, same bytes out.
- Needs Python 3 with Pillow and Microsoft Edge (`N7_STORE_BROWSER` may point to another Chromium browser).

## Input: `<app>/store/store.json`

| key | type | notes |
|---|---|---|
| `name` | string, required | app name, used in drawable comments and the contact sheet |
| `icon` | object | needed for `icon` and `drawables`: `frame` or `cells`, plus `signal` |
| `toys` | list | needed for `drawables` (`[]` for none): `{ "drawable": "toy_preview", "frame" or "cells", "signal"? }`; without a picture a toy takes the icon's, with its red element |
| `feature` | object | needed for `feature`: `title` (1 to 3 lines), `sub`, `frame` or `cells` |
| `shots` | list of 1 to 8 | needed for `shots`; shot 1 should be `phone` |

A picture is `"frame": "<name>"` (from `<app>/app/build/store/frames.json`, `{"name": [625 ints 0..4095]}`, written by the
app's `StoreFramesTest`; level = (v / 4095) ^ 0.45, at least 0.12) or `"cells": [[x, y], [x, y, level0to1], ...]`
(level 1 when left out). `signal` (`[[x, y], ...]`) is allowed in `icon` and `toys` only: red is never drawn on the device.

Every shot has `layout`, `headline` (1 to 3 lines, Doto; any Cyrillic line switches the headline to Geist) and `sub`:

| layout | extra keys | picture |
|---|---|---|
| `phone` | `frame` / `cells` | phone photo with the frame on the lens, the same matrix zoomed to the right |
| `zoom` | `frame` / `cells` | one matrix, up to 800 px, centred under the text |
| `grid` | `frames` (2 to 9: names, cell lists or `{frame}` / `{cells}` objects), `labels` (same count, `""` for none), `dim` (`true` or indexes of muted labels) | 2 per row for 2 and 4 pictures, else 3; a short last row is centred; matrices without glow |
| `screen` | `image` (path relative to the app project) | the app's own screenshot in a plain rounded frame (radius 56, 2 px rim), running off the bottom when tall |
| `steps` | `steps` (1 to 4 strings), `frame` / `cells` | a medium matrix above numbered lines (`01` in mono, body text) |

Keys starting with `_` are ignored (comments).

## Output (relative to the app project)

- `app/src/main/play/listings/en-US/graphics/phone-screenshots/<n>.png` 1080x1920 RGB (old higher numbers are removed)
- `app/src/main/play/listings/en-US/graphics/feature-graphic/1.png` 1024x500
- `app/src/main/play/listings/en-US/graphics/icon/1.png` and `app/src/main/ic_launcher-playstore.png` 512x512
- `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `<toy drawable>.xml`
  (108 dp vectors of circle paths; the adaptive icon in `res/mipmap-anydpi-v26/` comes from the app template)
- `store/out/contact.png`: everything on one sheet, with the launcher icon under round, squircle and themed masks

## How it works

`storekit/model.py` checks store.json (every problem at once, with its place), `browser.py` writes one page per picture
that loads `ds/tokens.css`, `ds/components/bundle.css`, React and `ds/components/bundle.js` by `file://` URLs plus
`storekit/page.css` and `page.js`, and captures it with headless Edge; the page reports success through a status square
under the picture and a console line, so a font or image that did not load fails the run. The Play icon (`icon.py`) and
the vector drawables (`drawables.py`) are drawn in Python. Layout numbers are fitted to Glyph Pet's published pictures,
which this kit reproduces to within a few levels a pixel, except that text is antialiased in grey (Edge runs with
`--disable-lcd-text`) where Glyph Pet's text has red and blue subpixel fringes: red belongs to the signal alone.

Tests (the page tests start headless Edge): `python -m unittest discover -s tests -v` in this folder.
