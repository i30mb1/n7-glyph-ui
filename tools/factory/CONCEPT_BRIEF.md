# What a toy concept must contain

You are designing one app for the Glyph Matrix on the back of the Nothing Phone (3). The concept goes straight to an engineer who
builds it in one pass, so it must be complete and unambiguous: every number chosen, every state drawn.

Read first: `PLATFORM_FACTS.md` (hard constraints) and section 3 of `KIT_SPEC.md` (the `ToyEngine` interface every toy implements,
`Motion`, `Haptic`) and section 1-2 (`MatrixCanvas`, `MatrixFont`: what can be drawn). Both are next to this file.
Look rules of the family: `D:\AndroidProject\n7-glyph-ui\store\ds\README.md`.
House style of an existing app of the family (screen, strings, listing): `D:\AndroidProject\glyph-pet` (`app/src/main/kotlin/n7/glyphpet/MainActivity.kt`,
`app/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `app/src/main/play/listings/`).

## Fixed frame of every app in this family
- One Android app = one or several Glyph Toys (each toy is its own entry the player can add to the phone's toy carousel).
- Each toy is a pure-Kotlin `ToyEngine`: long press, release, motion in; 25x25 brightness frames out; time only through `frame(dtMs)`.
- The same engine also runs live on the app's main screen (`ToyStage`): there the viewer looks at the SCREEN, a big on-screen button stands in for
  the Glyph Button, and the phone's sensors drive it. So the toy must be fun to try on the screen too, and must make sense with the phone held either way.
- App main screen, top to bottom: app name in dot type; "turn on the toy" card; the live stage; a "How it works" card with 3 short steps;
  app-specific cards (records, settings, mode choice); "More from n7"; signature line. No other screens unless really needed.
- Free, no ads, no accounts, no network, no purchases. Data stays on the phone (SharedPreferences).
- Size: about 1000-1800 lines of Kotlin including tests. Prefer a small set of mechanics polished to perfection over many half-done ones.

## The concept document (Markdown, in this order)
1. **Identity**: Play title (30 chars max, starts with "Glyph"), the toys (name up to 16 chars, one-line summary EN and RU each),
   one-sentence pitch EN and RU, why a viewer of a 15-second vertical video would want it.
2. **First ten seconds**: exactly what a new player sees on the matrix and does, second by second.
3. **Controls**: a table: input (long press / hold then release / tilt / shake / spin / stillness / free fall ...) x state -> what happens.
   Remember: a tap never reaches the toy; a hold always begins with the long press.
4. **State machine**: every state, every transition, all timers in ms and thresholds in m/s2 or rad/s, with the reasoning for each number
   and what happens at the limits (sensor missing, phone lying flat, player does nothing for a minute). Sources of randomness.
5. **Pictures**: for every state a 25x25 ASCII sketch (`#` bright, `+` medium, `.` dim, space off; respect the round outline) or exact drawing
   instructions with coordinates, plus how it animates (speeds in LEDs per second or periods in ms, never "per frame"). Brightness levels used (0..4095).
   Say how each piece of feedback reads ON CAMERA from 30 cm, since vibration cannot be filmed.
6. **Always-on**: whether the toy supports the always-on mode, and the calm frame it shows.
7. **Saved data**: keys, types, defaults.
8. **App screen**: the blocks, with every string in English and Russian (Russian short, addressing the player as «ты»).
9. **Icon**: the launcher/Play icon as a 25x25 picture in white LEDs with exactly ONE red element that is the point of the toy; give the cells.
10. **Store**: 5 screenshots (layout `phone`, `zoom`, `grid`, `screen` or `steps`; headline of 1-3 lines of one or two Latin words; sub line),
    which engine frames they show; short description EN and RU (80 chars max); full description EN and RU (sections with CAPS headings like glyph-pet's, honest, no claims the app cannot keep); feature-graphic line.
11. **Engine test plan**: concrete cases: given these inputs over this time, expect this state / these frame properties / these haptics.
12. **Check on the real phone**: what cannot be known without the device and which numbers will probably need tuning.
13. **Not in version 1**: ideas deliberately left out.

Write for an engineer, not for a pitch deck. No filler.
