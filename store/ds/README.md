N7 Glyph is the shared look of every n7 app made for the Nothing Phone Glyph Matrix (Glyph Snake, Flappy Box and the ones after them). It has two jobs: someone who sees one app's icon or screenshot should recognise the next one as coming from the same developer, and should see at a glance that the app runs on the Glyph Matrix on the back of the phone.

## The three signatures

Every app, screen and store image carries all three. Together they are the family.

1. **The round matrix.** The 25x25 Glyph Matrix with its real round outline and unlit cells visible (`GlyphMatrix`). It is in the icon, on the main screen and on every screenshot.
2. **The red dot.** One `signal` element per view: the dot in the signature line, the full stops of a headline, the single red pixel in an icon. One, never more; never text, never a button.
3. **The dot type.** App names, headlines and scores are set in Doto (`display-*`, `score`), the open-source dot-matrix face closest to Nothing's own.

## Content

- English UI copy is short and imperative: TILT. EAT. GROW. Headlines are 1 to 3 lines of one or two words.
- Russian copy is just as short: «Наклоняй телефон», «Ешь и расти». Address the player as «ты».
- Name things the way Nothing OS does: "Glyph Toy", "Glyph Matrix", "Glyph button". Say "Nothing Phone (3)", never a codename.
- No emoji in the UI. The store description may use them as section markers only.
- Numbers on screen are zero-padded to three digits (`042`).

## Visual foundations

**Colour.** Black first. Use `ground` for every background, `surface` and `surface-raised` for cards, `ink` for text, `ink-muted` for secondary copy and `ink-subtle` for mono labels. `primary` (white) fills the one main button, with `on-primary` text. `signal` red is the only colour: it marks, it never fills. Inside a matrix use `led-on`, `led-mid` and `led-off`. Draw `signal` cells in icons and brand art, never in a picture of the real device, whose LEDs are white-only.

**Type.** Doto (`display-xl`, `display-l`, `score`, `display-s`) for Latin names, headlines and numbers, always with round dots (`.n7-dot`). Doto has no Cyrillic: any Russian headline is set in `headline` (Geist 600/700), which `DotHeadline` does by itself. Body copy is Geist (`title`, `body`, `body-s`). Labels and the signature are Geist Mono caps (`label`, `caption`).

**Layout.** Android screens use a `space-4` gutter and `space-6` screen padding. Main screen order, top to bottom: app name (`display-l`), a `label` line, a large live `GlyphMatrix`, `ScoreDisplay`, then the buttons pinned to the bottom (`primary` then `secondary`, `space-2` apart), then the `SignatureLabel`.

**Shape.** Cards use `radius-card` (28px, as in Nothing OS), buttons `radius-pill`. No shadows: separation comes from `surface` steps and `hairline`. Controls get a `control-border` outline. The focus ring is a solid 2px `ink` outline, 3px offset.

**Motion.** The matrix may animate (the snake moves, the food pulses). Everything else stays still.

## App icons

Build every icon with `AppIcon`: black ground, the round lens and matrix, the app's picture in white LEDs, and exactly one red `signal` element that is the point of the game (Snake: the food; Flappy Box: the bird). No text, no other colours. Export 512x512 `square` for Google Play and `fill` 0.62 for the adaptive launcher icon.

## Store screenshots

Use `StoreShot` (1080x1920). Every shot of every app has the same top: `SignatureLabel` top-left, a `DotHeadline`, a `ink-muted` sub line. Then the picture: `PhoneBack` (the real phone photo) with a zoomed `GlyphMatrix`. Order: gameplay first (shots 1 to 3), then features, then how to start it as a Glyph Toy. Put a "More from n7" row of `ToyCard`s in each app's settings so the apps advertise each other in the same look.

## Iconography

There is no icon set yet: the apps use text buttons. When one is needed, draw it on the 25x25 grid as LED cells (`GlyphMatrix` with `lens={false}`) rather than borrowing a line-icon set.

## Logos

n7 has no logo. The developer name is set in plain `caption` type ("n7"); the `SignatureLabel` does the work a logo would.

## Loading it

On a Design canvas or any web page, load in this order: `tokens.css` (it declares the fonts from `fonts/`), `components/bundle.css`, then `components/bundle.js` after React 18. The components are on `window.N7Glyph`. In the Android apps, the same values live in the Compose library `n7-glyph-ui`: colours and type from `tokens.json`, the fonts from `fonts/`.

## Assets

`Device/phone-3-white-back.webp` is the developer's own photo of a white Nothing Phone (3), cut out and turned upright, with the matrix lens left dark for a `GlyphMatrix` overlay (centre at 79.7% / 8.8% of the image, lens radius 13.9% of its width). Use it through `PhoneBack`.
