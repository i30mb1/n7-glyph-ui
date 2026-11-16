# The n7 Glyph app template and its generator

Goal: a buildable skeleton project at `D:\AndroidProject\n7-glyph-ui\template\` from which every new n7 Glyph Matrix app starts,
plus `D:\AndroidProject\n7-glyph-ui\tools\new_app.py` that copies it into a new project with its own package and name.

Reference project (read it first, it is the house style and it is in production): `D:\AndroidProject\glyph-pet`.
Reuse its build setup, manifest shape, theme, screen composition and toy-service plumbing; drop the pet logic and billing.
The toy runtime (engine interface, motion, haptics, on-screen stage, canvas, fonts) is already in the library: read
`D:\AndroidProject\n7-glyph-ui\n7-glyph-ui\src\main\kotlin\n7\glyph\ui\toy` and `...\matrix` and the spec `KIT_SPEC.md` next to this file.

## Rules that apply to every Gradle call
Run Gradle ONLY through the lock wrapper (other agents build in parallel and share the included library build):
`python D:\AndroidProject\n7-glyph-ui\tools\gw.py <project-dir> <gradle args...>` with a 10-minute tool timeout.
Never run `gradlew` directly, never run `gradle --stop`, never delete `D:\AndroidProject\n7-glyph-ui\n7-glyph-ui\build`.
Do not commit, tag or push anything anywhere. Do not touch `D:\AndroidProject\glyph-pet` or other existing apps.

## Template project layout (package `n7.glyphtemplate`, app name "Glyph Template", repo name `glyph-template`)

```
template/
  .gitignore                      as glyph-pet (must list play-service-account.json and local.properties)
  settings.gradle.kts             as glyph-pet: JitPack repo limited to group com.github.i30mb1, includeBuild("../n7-glyph-ui") when it exists
  build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/*, gradle/libs.versions.toml, gradle/gradle-daemon-jvm.properties
  libs/glyph-matrix-sdk-2.0.aar   copy from glyph-pet
  docs/privacy.html               privacy policy page (GitHub Pages), same look as glyph-pet's, no purchases section
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/keepRules/rules.keep   (or app/proguard-rules.pro if the legacy R8 flags are used, see below)
  app/src/main/kotlin/n7/glyphtemplate/MainActivity.kt
  app/src/main/kotlin/n7/glyphtemplate/game/DemoEngine.kt
  app/src/main/kotlin/n7/glyphtemplate/toy/GlyphToyService.kt     base class, identical in every app
  app/src/main/kotlin/n7/glyphtemplate/toy/DemoToyService.kt
  app/src/main/res/...            themes.xml, strings (values + values-ru), adaptive launcher icon, toy_preview drawable
  app/src/main/play/...           contact-email.txt, contact-website.txt, default-language.txt, listings/en-US + ru-RU texts, release-notes
  app/src/test/kotlin/n7/glyphtemplate/game/DemoEngineTest.kt
  app/src/test/kotlin/n7/glyphtemplate/StoreFramesTest.kt
  store/store.json                input of the store-graphics kit (see STORE_SPEC.md); a minimal valid example
```

The template sits INSIDE the library repo, so for it the library checkout is `..`, while for a generated app (a sibling of the
library) it is `../n7-glyph-ui`. Make `settings.gradle.kts` pick the first that exists: `../n7-glyph-ui/settings.gradle.kts`
(generated app), else `../n7-glyph-ui/build.gradle.kts` next to `../settings.gradle.kts` (the template inside the library repo, where
`..` is the library root). Keep it short and commented. A generated app must work with only the first rule.
The library dependency is `com.github.i30mb1:n7-glyph-ui:0.3.0` (not on JitPack yet: the local checkout substitutes it).

### app/build.gradle.kts
- Same as glyph-pet: Gradle Play Publisher (`com.github.triplet.play` 4.1.2) with `serviceAccountCredentials = rootProject.file("play-service-account.json")`,
  `defaultToAppBundles = true`, `track = "production"`, `releaseStatus = COMPLETED`; upload-key signing from `~/.gradle/gradle.properties`
  (`n7.upload.*`, falling back to `kcal.upload.*`, then the debug key); compileSdk 37, minSdk 35, targetSdk 37, versionCode 1, versionName "1.0";
  Java toolchain 17; compose + buildConfig on; dependencies: the SDK AAR, n7-glyph-ui 0.3.0, core-ktx, lifecycle-runtime-ktx,
  activity-compose, compose BOM/ui/material3, junit for tests. No billing.
- R8 must be on for release. This project uses AGP 9.5, where the owner's rule is `optimization { enable = true }` in the release build type
  (see `D:\AndroidProject\essential_widgets\app\build.gradle.kts` for a fresh Android Studio example of that DSL and its `keepRules` folder).
  Try that first; prove that R8 really ran (a `mapping.txt` exists under `app/build/outputs/mapping/release`, the release APK is much smaller
  than debug, classes are renamed). Only if it cannot be made to work, fall back to glyph-pet's `isMinifyEnabled` + `isShrinkResources` +
  `proguard-android-optimize.txt` and say so in your report. Keep rule for the SDK: `-keep class com.nothing.ketchum.** { *; }` (it is called over Binder).
- The release build must not fail when `play-service-account.json` is absent (it is needed only by publish tasks).

### AndroidManifest.xml
`com.nothing.ketchum.permission.ENABLE`, `android.permission.VIBRATE`, `<uses-feature android:name="com.nothing.feature" android:required="true"/>`,
portrait `MainActivity` (launcher), one `<service>` per toy exactly like glyph-pet's (`com.nothing.glyph.TOY` action, meta-data name, image,
summary, `longpress` = 1, optional `aod_support` = 1). `android:allowBackup="true"` as in glyph-pet.

### toy/GlyphToyService.kt (the SDK plumbing; copy the proven behaviour of glyph-pet's `PetToyService`)
```kotlin
abstract class GlyphToyService : Service(), GlyphMatrixManager.Callback {
    /** A fresh engine for this session of the toy. */
    protected abstract fun createEngine(): ToyEngine
    protected open val frameMs: Long get() = 40L
}
```
- `onBind`: create the engine, `ToyMotionSource(this, Facing.BACK, engine.sensors, engine.sensorHz, engine::onMotion)`, `ToyHaptics(this)`,
  `GlyphMatrixManager.getInstance(this).init(this)`, return a `Messenger` whose handler maps
  `GlyphToy.EVENT_CHANGE` -> `engine.onLongPress()` (and starts the live loop if it was stopped), the SDK's button-up event -> `engine.onRelease()`,
  `GlyphToy.EVENT_AOD` -> stop the live loop and send `engine.still()`.
  Find the exact constant names for the button events by inspecting the AAR (`unzip` classes.jar, `javap -p -constants com.nothing.ketchum.GlyphToy`);
  SDK 2.0 has action-down and action-up events. Ignore action-down.
- `onServiceConnected`: `register(Glyph.DEVICE_23112)`, `setGlyphMatrixTimeout(false)`, start the live loop.
- Live loop on the main looper every `frameMs`: `dt` = real elapsed ms clamped to 0..250, `send(engine.frame(dt))`, then play `engine.drainHaptics()` with `ToyHaptics.play(list)` (one vibration, the strongest of the batch).
- `onServiceDisconnected` / `onUnbind`: stop the loop and the sensors; on unbind also `turnOff()`, `closeAppMatrix()` when connected, then `unInit()`.
- `send` wraps `setMatrixFrame` in try/catch and logs. No coroutines, no threads: one main-thread Handler, like glyph-pet.

### game/DemoEngine.kt + toy/DemoToyService.kt
A tiny but real toy that exercises everything: a ball that rolls with gravity inside the round matrix (uses `Motion.downX/downY`),
bounces off the edge with a TICK; a long press makes a ring pulse from the centre and, while held, charges the rim; release fires the ring.
`still()` shows the ball at rest in the middle. Seeded `kotlin.random.Random` in the constructor. This is example code that the generator
keeps so a new app compiles; app builders replace it.

### MainActivity.kt (the house screen, one file, Compose, `N7GlyphTheme`)
Order top to bottom, as the design system prescribes and as glyph-pet does:
1. `DotHeadline(lines = ["GLYPH", "TEMPLATE"], fontSize = 48.sp)`.
2. Setup card until the player opened the toys manager once (`N7Card`: title, text, primary `N7Button` "Turn on" that opens
   `com.nothing.thirdparty/com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity`, try/catch). Same strings idea as glyph-pet ("Step 1. Turn on the toy").
3. `ToyStage(engine, holdLabel = ..., hint = ...)`: the live toy on screen.
4. A "How it works" `N7Card` with three numbered steps (use glyph-pet's `Step` row look).
5. Slot for app-specific cards (records, settings).
6. After setup: secondary `N7Button` "Glyph Toys" opening the same manager.
7. `MoreFromN7(Modifier.fillMaxWidth())`, then `SignatureLabel` centred.
Screen: `Surface(color = N7Colors.Ground)`, vertical scroll, `systemBarsPadding`, padding 24 dp (top 32), 24 dp between blocks, `enableEdgeToEdge()`.
All text from string resources, English in `values/`, Russian in `values-ru/` (short, «ты»).

### Tests
- `DemoEngineTest`: frames stay inside the visible matrix and within 0..4095 for a long random run; gravity moves the ball the right way;
  long press then release produces the ring; `still()` does not change state.
- `StoreFramesTest`: renders named frames with the real engine and writes `app/build/store/frames.json` as `{"name": [625 ints], ...}`
  (plain JSON written by hand, no extra dependency). The store kit reads this file.

### Launcher icon and toy preview
Adaptive icon exactly in glyph-pet's manner (`res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`, black background colour,
`drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`) and `drawable/toy_preview.xml`. For the template use a simple
picture (a ball, with one red LED in the launcher icon only). The store kit regenerates these files for real apps; keep the file names.

## tools/new_app.py
`python new_app.py --dir D:\AndroidProject\glyph-flame --package n7.glyphflame --name "Glyph Flame" --repo glyph-flame`
- Copies `template/` (without build outputs, `.gradle`, `.kotlin`, `.idea`, `local.properties`) to `--dir`; refuses to overwrite an existing directory.
- Renames the package directories and every occurrence of `n7.glyphtemplate`, `Glyph Template`, `GLYPH`/`TEMPLATE` headline lines,
  `glyph-template`, `GlyphTemplate` (theme and class names) to the new values. The headline becomes the app name split into words, upper-case.
- Writes `local.properties` with the same `sdk.dir` as glyph-pet's.
- Prints the next steps (build command through gw.py).
Prove it: generate `D:\AndroidProject\_n7_template_check` (package `n7.glyphcheck`, name "Glyph Check"), build `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`
through gw.py, confirm the APKs exist, then delete that directory again (only that one).

## Done means
- Template builds: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` green through gw.py.
- Generated check app builds the same three tasks.
- Report: what you built, every deviation from this spec and why, the exact R8 setup you ended up with and the evidence that R8 ran,
  the SDK event constant names you found, anything a builder of a real app must know. Short and factual.
