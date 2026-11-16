# n7-glyph-ui 0.3.0: the toy kit

Add an SDK-free toy runtime to the shared library `D:\AndroidProject\n7-glyph-ui` (module `n7-glyph-ui`, Kotlin explicit API mode,
namespace `n7.glyph.ui`, minSdk 33). Every new n7 Glyph app will build its toys on it, so the API below is a contract: implement it
as written (names, signatures, semantics). You may add private helpers and extra public members when they are clearly useful, but
do not rename or drop anything.

The library must NOT depend on the Glyph Matrix SDK AAR and must NOT declare permissions in its manifest.
Existing public API (theme, components, family, `GlyphMatrixSpec`, `Cell`, `Frames`) must stay source compatible.

## 1. `n7.glyph.ui.matrix.MatrixCanvas` (pure Kotlin, no Android imports)

A 25x25 picture being drawn. Brightness per LED 0..4095. Everything outside the round outline (`GlyphMatrixSpec.isVisible`) is
silently ignored on write and reads as 0.

```kotlin
public class MatrixCanvas {
    public val pixels: IntArray                      // 625 values, row by row
    public fun clear(): MatrixCanvas
    public operator fun get(x: Int, y: Int): Int
    public operator fun set(x: Int, y: Int, level: Int)       // overwrite, level clamped to 0..MAX
    public fun add(x: Int, y: Int, level: Int)                // additive, clamped
    public fun lighten(x: Int, y: Int, level: Int)            // keeps the brighter of old and new
    public fun fill(level: Int)                               // every visible LED
    public fun line(x0: Int, y0: Int, x1: Int, y1: Int, level: Int)          // Bresenham, any direction, uses lighten
    public fun rect(x: Int, y: Int, w: Int, h: Int, level: Int)              // outline
    public fun fillRect(x: Int, y: Int, w: Int, h: Int, level: Int)
    public fun circle(cx: Float, cy: Float, r: Float, level: Int)            // 1-LED outline
    public fun disc(cx: Float, cy: Float, r: Float, level: Int)              // filled
    public fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, level: Int)  // 0 deg = 12 o'clock, clockwise
    public fun rim(fraction: Float, level: Int, startIndex: Int = 0)         // lights the first `fraction` (0..1) of RIM, clockwise
    public fun sprite(rows: List<String>, x: Int, y: Int, level: Int = MAX)  // '#' = level, '+' = 60% of level, '.' = 25%, anything else skipped
    public fun text(text: String, x: Int, y: Int, level: Int = MAX, font: MatrixFont = MatrixFont.Small): Int  // returns the x after the last glyph
    public fun textCentered(text: String, y: Int, level: Int = MAX, font: MatrixFont = MatrixFont.Small)      // centred on x = 12
    public fun frame(): IntArray                     // a copy, ready for setMatrixFrame
    public fun cells(): List<Cell>                   // lit LEDs for the Compose `GlyphMatrix`

    public companion object {
        public const val SIZE: Int = 25
        public const val MAX: Int = 4095
        /** The 68 LEDs on the edge of the round matrix, clockwise, starting at 12 o'clock (12, 0). */
        public val RIM: List<Pair<Int, Int>>
        /** Frame brightness to `GlyphMatrix` cells. Perceptual: level = (v / MAX)^0.45, at least 0.12 for any lit LED. */
        public fun cells(frame: IntArray): List<Cell>
    }
}
```

`RIM` must be a single closed loop: 68 distinct edge cells, first is (12, 0), each next one is the clockwise neighbour.
(Glyph Pet has a working implementation to compare with: `D:\AndroidProject\glyph-pet\app\src\main\kotlin\n7\glyphpet\pet\ChargeRenderer.kt`, `RING`.)

## 2. `n7.glyph.ui.matrix.MatrixFont` (pure Kotlin)

```kotlin
public class MatrixFont private/internal constructor(...) {
    public val height: Int
    public fun width(text: String): Int          // total width in LEDs, 1 LED between glyphs, no trailing gap
    public fun glyph(char: Char): List<String>?  // rows of '#' and ' '; null when the font has no such character
    public companion object {
        /** 3x5: digits, A-Z (lowercase is drawn as uppercase), space and . , : ! ? - + % / ' */
        public val Small: MatrixFont
        /** 5x7: digits and . : - (for scores and timers). Four digits fit the 25-LED width. */
        public val Big: MatrixFont
    }
}
```
Unknown characters are skipped with the width of a space. Glyphs must be clean and readable: check each digit by eye in a test dump.
Narrow glyphs ('1', '.', ':', '!', ''') may be narrower than the font's nominal width.

## 3. `n7.glyph.ui.toy`: motion, haptics, engine (pure Kotlin except where marked)

```kotlin
/** Which face of the phone the picture is on: the Glyph Matrix on the back, or the app's on-screen preview. */
public enum class Facing { BACK, SCREEN }

/**
 * The phone's movement at one instant, in the frame of the picture as its viewer sees it:
 * x to the viewer's right, y down, z towards the viewer.
 */
public class Motion(
    public val timeMs: Long,
    /** Where things fall, m/s2. Phone upright and at rest: (0, 9.8, 0). Picture facing the ceiling, viewer above: (0, 0, -9.8). */
    public val downX: Float,
    public val downY: Float,
    public val downZ: Float,
    /** Turning around the viewing axis, rad/s, positive = clockwise as the viewer sees the picture. 0 without a gyroscope. */
    public val spin: Float = 0f,
    /** Turning around the two other axes, rad/s, always >= 0 (how much the picture tips and swings). 0 without a gyroscope. */
    public val wobble: Float = 0f,
) {
    /** Length of the down vector, m/s2: about 9.8 at rest, near 0 in free fall, large in a shake. */
    public val force: Float
    public companion object {
        public const val G: Float = 9.80665f
        /** From raw device-axis values (accelerometer m/s2, gyroscope rad/s) as Android reports them. */
        public fun fromDevice(timeMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, facing: Facing): Motion
        /** A phone held still and upright. Handy in tests. */
        public fun rest(timeMs: Long = 0L): Motion
    }
}
```

Axis maths (Android device axes: looking at the SCREEN in portrait, +x right, +y up, +z out of the screen; the accelerometer
reports the reaction force, so a phone lying screen-up reads az = +9.8, upright reads ay = +9.8):
- gravity in device axes = (-ax, -ay, -az)
- `Facing.SCREEN`: downX = -ax, downY = ay, downZ = -az, spin = -gz
- `Facing.BACK`:   downX =  ax, downY = ay, downZ =  az, spin = +gz
- wobble = sqrt(gx*gx + gy*gy) for both
Write unit tests for these with physical cases (upright, left edge down, screen up, screen down, clockwise turn seen from each face).

```kotlin
public enum class Haptic { TICK, CLICK, THUD, BUZZ, SUCCESS, FAIL }

/** Which sensors a toy needs. */
public enum class ToySensors { NONE, ACCELEROMETER, ACCELEROMETER_AND_GYROSCOPE }

/**
 * One Glyph Toy as pure logic: button and motion in, 25x25 frames out. No Android inside, so the same engine runs in the
 * toy service, on the app screen and in unit tests. Time only moves through [frame]; never read a clock inside an engine.
 */
public interface ToyEngine {
    public val sensors: ToySensors get() = ToySensors.ACCELEROMETER
    /** Sensor readings per second the toy wants: 50 is enough for tilt, up to 200 for impacts and free fall. */
    public val sensorHz: Int get() = 50
    /** Long press of the Glyph Button. If the player keeps holding, [onRelease] follows when they let go. */
    public fun onLongPress() {}
    /** The button was let go after a long press. */
    public fun onRelease() {}
    public fun onMotion(motion: Motion) {}
    /** Advances the toy by [dtMs] and returns the next frame: 625 values 0..4095, row by row. */
    public fun frame(dtMs: Long): IntArray
    /** One calm frame for the always-on display. Must not change the state of the toy. */
    public fun still(): IntArray
    /** Haptics asked for since the last call, oldest first. The host plays them; a toy must not depend on them being felt. */
    public fun drainHaptics(): List<Haptic> = emptyList()
}
```

### Android parts (same package)

```kotlin
/** Feeds an engine with [Motion]. Listeners are registered on start() and removed on stop(). Delivers on the main thread. */
public class ToyMotionSource(context: Context, facing: Facing, sensors: ToySensors, sensorHz: Int = 50, onMotion: (Motion) -> Unit) {
    public fun start()
    public fun stop()
}
```
- One `Motion` per accelerometer event, carrying the latest gyroscope reading. `timeMs` = event.timestamp / 1_000_000.
- Sampling period = 1_000_000 / sensorHz microseconds (never faster than 200 Hz). Missing sensors are tolerated silently.
- NONE registers nothing.

```kotlin
/** Plays [Haptic]s with the system vibrator. Does nothing without a vibrator or without the VIBRATE permission (never throws). */
public class ToyHaptics(context: Context) {
    public fun play(haptic: Haptic)
    /** One frame's batch as one vibration: the strongest (SUCCESS/FAIL > BUZZ > THUD > CLICK > TICK, newest of equals). */
    public fun play(haptics: List<Haptic>)
}
```
TICK/CLICK/THUD map to `VibrationEffect.createPredefined` (EFFECT_TICK, EFFECT_CLICK, EFFECT_HEAVY_CLICK); BUZZ is ~250 ms;
SUCCESS two rising short pulses; FAIL one long low pulse. Wrap every call in try/catch.

```kotlin
/**
 * The toy running on the app screen: the round matrix, driven by the phone's own sensors, with an on-screen stand-in for the
 * Glyph Button. Runs only while the screen is resumed.
 */
@Composable
public fun ToyStage(
    engine: ToyEngine,
    holdLabel: String,
    modifier: Modifier = Modifier,
    matrixSize: Dp = 224.dp,
    hint: String? = null,
    contentDescription: String? = null,
    haptics: Boolean = true,
)
```
- Frame loop about 30 fps while the lifecycle is RESUMED (`repeatOnLifecycle`), real elapsed time as `dtMs` (clamped to 0..250).
- Sensors through `ToyMotionSource(context, Facing.SCREEN, engine.sensors, engine.sensorHz)`, started and stopped with the loop.
- Draws `GlyphMatrix(cells = MatrixCanvas.cells(frame))` centred, `matrixSize` wide.
- Under it the hold button `GlyphHoldButton(label = holdLabel, onLongPress = engine::onLongPress, onRelease = engine::onRelease)`
  and, if given, `hint` in `N7Type.BodyS` / `N7Colors.InkMuted`, centred.
- Plays `engine.drainHaptics()` after each frame when `haptics` is true, as one vibration: `ToyHaptics.play(list)` (the
  strongest of the batch), since each vibration cuts off the one before.
- When `engine` changes (new instance), everything restarts for the new one.

```kotlin
/**
 * On-screen stand-in for the Glyph Button. Works like the real one: a press shorter than [longPressMs] does nothing,
 * holding longer fires [onLongPress], letting go after that fires [onRelease].
 */
@Composable
public fun GlyphHoldButton(
    label: String,
    onLongPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    longPressMs: Long = 350L,
)
```
Look: a pill in the family style (secondary button outline `N7Colors.ControlBorder`, label in `N7Type.BodyS`), at least 56 dp tall,
full width of the stage (max 320 dp). While pressed the pill fills with `N7Colors.SurfaceRaised`; once the long press fired it fills
with `N7Colors.Primary` and the label turns `N7Colors.OnPrimary`. Accessible: role Button, and a click action for TalkBack that fires
onLongPress immediately followed by onRelease.

## 4. Tests

Add a `src/test/kotlin` source set to the module (JUnit 4.13.2, `testImplementation`). Cover: canvas clipping and every primitive,
RIM (68 distinct cells, closed loop, starts at (12,0)), fonts (every glyph has `height` rows, width(text) correct, a dump of all
glyphs printed to stdout for eyeballing), Motion.fromDevice physical cases, `MatrixCanvas.cells` monotonic.
Run: `python D:\AndroidProject\n7-glyph-ui\tools\gw.py D:\AndroidProject\n7-glyph-ui :n7-glyph-ui:testDebugUnitTest :n7-glyph-ui:assembleRelease`
(if `tools/gw.py` does not exist yet, run `./gradlew` directly from the repo root).

## 5. Housekeeping
- Default library version in `n7-glyph-ui/build.gradle.kts` becomes `0.3.0`.
- README: add the new packages to the "What's inside" table (one row per package, same terse style). No other prose.
- Do not touch `family/N7Apps.kt` (the catalogue is updated separately) and do not commit or tag anything.
