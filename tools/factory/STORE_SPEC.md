# The store-graphics kit

Goal: one command that produces every picture an n7 Glyph app needs for Google Play and for its launcher, in the family look,
from a small JSON file in the app project. It lives in `D:\AndroidProject\n7-glyph-ui\store\`.

Already there (do not change these files):
- `store/ds/`: the N7 Glyph design system for the web: `tokens.css` (declares the fonts from `fonts/`), `components/bundle.css`,
  `components/bundle.js` (React 18 components on `window.N7Glyph`: `GlyphMatrix`, `AppIcon`, `PhoneBack`, `DotHeadline`, `SignatureLabel`,
  `ToyCard`, `ScoreDisplay`, `StoreShot`; typings in `components/index.d.ts`), `fonts/`, `assets/Device/phone-3-white-back.webp`
  (the owner's photo of the phone's back, 1240x2684, matrix lens left dark), and `README.md` with the rules of the look. READ `store/ds/README.md` FIRST.
- `store/vendor/react.production.min.js`, `react-dom.production.min.js` (React 18.3.1 UMD).
- `bundle.js` hard-codes the phone photo as `/_blob/...`; pass `src` to `PhoneBack` with the local file instead.

What the finished look is: open `D:\AndroidProject\glyph-pet\app\src\main\play\listings\en-US\graphics\` (5 phone screenshots 1080x1920,
feature graphic 1024x500, icon 512x512) and `D:\AndroidProject\glyph-pet\app\src\main\res\drawable\ic_launcher_foreground.xml`,
`ic_launcher_monochrome.xml`, `toy_preview.xml`, `res/mipmap-anydpi-v26/`. Those were made by an earlier, lost version of this kit.
Your output for the same content must look the same: same top block (signature line, dot headline with red full stops, muted sub line),
phone photo with the live matrix laid over the lens, a big zoomed matrix beside it.

## Input: `<app-project>/store/store.json`
```json
{
  "name": "Glyph Flame",
  "icon":   { "frame": "icon", "signal": [[12, 20]] },
  "toys":   [ { "drawable": "toy_preview", "frame": "icon" } ],
  "feature": { "title": ["GLYPH", "FLAME"], "sub": "A living flame on the back of your Nothing Phone (3).", "frame": "flame_tall" },
  "shots": [
    { "layout": "phone",  "headline": ["LIGHT", "IT", "UP"], "sub": "Hold the Glyph button to strike a match.", "frame": "flame_tall" },
    { "layout": "zoom",   "headline": ["TILT", "IT"], "sub": "The flame always reaches up.", "frame": "flame_lean" },
    { "layout": "grid",   "headline": ["3", "MODES"], "sub": "Candle, torch, campfire.", "frames": ["candle", "torch", "campfire"], "labels": ["CANDLE", "TORCH", "CAMPFIRE"] },
    { "layout": "screen", "headline": ["TRY", "IT"], "sub": "The app shows the toy live.", "image": "store/screens/main.png" },
    { "layout": "steps",  "headline": ["TURN", "IT ON"], "sub": "Add it to your Glyph Toys.", "steps": ["Open Glyph Toys", "Add Glyph Flame", "Press the Glyph button"], "frame": "icon" }
  ]
}
```
- Frames come from `<app-project>/app/build/store/frames.json`: `{"name": [625 ints 0..4095], ...}`, written by the app's unit test
  `StoreFramesTest` with the REAL engine. A frame may also be given inline as `"cells": [[x, y, level0to1], ...]`.
  Brightness to cell level: level = (v / 4095) ^ 0.45, at least 0.12 for any lit LED (the same curve as `MatrixCanvas.cells` in the library).
- `icon.signal`: the single red element of the app icon (cells drawn in the signal red). Red appears ONLY in the icon, never on the phone photo
  or in screenshots of the matrix (the hardware is white-only).
- Layouts (all 1080x1920, all with the same `StoreShot` top block):
  - `phone`: `PhoneBack` from about y 880 (width ~620, bottom cut by the frame) with the frame on its lens, and a zoomed `GlyphMatrix` (~540) overlapping to the right. This is shot 1 of every app.
  - `zoom`: one huge `GlyphMatrix` (~800) centred in the stage.
  - `grid`: 2 to 9 matrices in a grid with mono caps labels under each (like glyph-pet's shot 2).
  - `screen`: a real screenshot of the app (PNG, taken later on an emulator) shown in a plain rounded phone frame (radius 56, 2 px `--lens-rim` border, no notch art).
    If the image file is missing, fail with a clear message naming the file.
  - `steps`: three numbered lines in the family type (mono number, body text) with a medium matrix above. For "how to turn it on".
- Text may be English only for now. Headlines are Latin and set in Doto; if a headline contains Cyrillic, `DotHeadline` switches to Geist by itself.

## Output (paths relative to the app project)
- `app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png` ... (1080x1920, RGB PNG, no alpha)
- `app/src/main/play/listings/en-US/graphics/feature-graphic/1.png` (1024x500): signature line, the title in dot type with red full stops, sub line on the left; the phone photo cropped on the right showing the matrix with `feature.frame` (see glyph-pet's).
- `app/src/main/play/listings/en-US/graphics/icon/1.png` and `app/src/main/ic_launcher-playstore.png` (512x512, `AppIcon` shape square, fill 0.82).
- `app/src/main/res/drawable/ic_launcher_foreground.xml` (adaptive icon foreground, 108dp viewport, the lens + matrix scaled so it stays inside the 66dp safe zone: fill 0.62; white LEDs, unlit cells, one red signal element),
  `app/src/main/res/drawable/ic_launcher_monochrome.xml` (same picture, single colour, lit cells only plus the lens ring), and one
  `app/src/main/res/drawable/<toys[i].drawable>.xml` per toy (the picture the Glyph Toys list shows: copy the structure of glyph-pet's `toy_preview.xml`).
  Vector drawables must match the structure glyph-pet uses (path data of circles), so they compile with AAPT.
- `store/out/contact.png`: a contact sheet of everything, for review.

## How to render
- `python D:\AndroidProject\n7-glyph-ui\store\render.py <app-project-dir> [--only shots|feature|icon|drawables]`
- Build one HTML page per picture in a temp folder inside `store/out/` of the app, loading `tokens.css`, `bundle.css`, React, `bundle.js` by `file://` URLs,
  then screenshot it with headless Edge: `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe --headless=new --disable-gpu --hide-scrollbars
  --force-device-scale-factor=1 --window-size=W,H --virtual-time-budget=4000 --screenshot=<png> <file-url>` (fonts must be loaded before the shot;
  verify by looking at the result: dot type must be Doto, not a fallback monospace).
- Convert to RGB without alpha with Pillow (installed). Python 3.14 standard library + Pillow only. No Node build step.
- Vector drawables are generated in Python directly (no browser).
- Idempotent, clear errors, exit code non-zero on failure.

## Proof (required before you report)
1. Make a throwaway project folder under the scratchpad (`<scratch folder>`)
   with a `store.json` that recreates Glyph Pet's shot 1 ("AN EGG / EVERY / DAY", sub "A new egg every morning. By the evening a pet hatches."),
   its feature graphic and its icon, using inline cells for an egg picture.
2. Render, then LOOK at your PNGs and at glyph-pet's originals side by side (Read tool on the image files). Fix every visible difference in
   layout, type, sizes, spacing, phone position and matrix look. Repeat until they match closely.
3. Run every layout once and look at each result.
4. Compile-check the generated vector drawables: copy them into `D:\AndroidProject\n7-glyph-ui\template\app\src\main\res\drawable\` ONLY if that
   template already exists and builds; otherwise validate them as well-formed XML with the same root attributes as glyph-pet's.

## Report
What exists, the command line, the store.json schema as implemented (every key), known limits, and the list of proof images with their paths.
Do not commit anything. Do not write outside `D:\AndroidProject\n7-glyph-ui\store\` and the scratchpad proof folder.
