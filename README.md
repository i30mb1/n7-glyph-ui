# n7-glyph-ui

Shared Compose look of every n7 app made for the Nothing Phone Glyph Matrix (Glyph Snake, Flappy Box, ...).
The design source is the **N7 Glyph** design system on claude.ai; this library mirrors its `tokens.json`.

## What's inside

| Package | What |
|---|---|
| `n7.glyph.ui.theme` | `N7GlyphTheme`, colour tokens `N7Colors`, `N7Spacing`, `N7Radius`, text styles `N7Type`, fonts Doto / Geist / Geist Mono |
| `n7.glyph.ui.matrix` | `GlyphMatrixSpec` (real round outline of the Phone (3) matrix), `Cell`, `Frames` (`line`, `snake`, family icons) |
| `n7.glyph.ui.components` | `GlyphMatrix`, `DotHeadline`, `SignatureLabel`, `N7Button`, `N7Stepper`, `N7Card`, `N7Divider`, `ScoreDisplay`, `SectionLabel`, `ToyCard` |
| `n7.glyph.ui.family` | `N7Apps` (every family app) and `MoreFromN7` (cross-promotion list that opens Google Play) |

## Rules

- Black first. `N7Colors.Signal` (red) is the family signature: one per view, never text, never a fill, never an LED in a picture of the real device.
- Doto (`N7Type.Display*`, `Score`) for Latin names, headlines and numbers. Doto has no Cyrillic: `DotHeadline` switches to Geist Bold by itself.
- Every main screen ends with `MoreFromN7()` and `SignatureLabel()`.
- App icon: black ground, round matrix, the app's picture in white LEDs, exactly one red element.

## Use

```kotlin
// settings.gradle.kts
dependencyResolutionManagement { repositories { maven("https://jitpack.io") } }

// build.gradle.kts
implementation("com.github.i30mb1:n7-glyph-ui:<tag>")
```

```kotlin
setContent { N7GlyphTheme { MyScreen() } }
```

Local development next to an app: `includeBuild("../n7-glyph-ui")` in the app's `settings.gradle.kts` with a dependency substitution for `com.github.i30mb1:n7-glyph-ui`.

## New family app

Add it to `N7Apps.all` (package, title, summary, icon cells, red signal cells), tag a new version, bump it in every app.

Fonts: Doto, Geist, Geist Mono, SIL Open Font License 1.1.
