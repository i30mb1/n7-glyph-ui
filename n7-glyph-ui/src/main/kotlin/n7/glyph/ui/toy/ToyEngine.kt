package n7.glyph.ui.toy

/** Feedback a toy asks the host to play on the vibrator. */
public enum class Haptic {
    /** A faint tick: a step, a tilt notch. */
    TICK,

    /** A crisp click: a pick-up, a hit. */
    CLICK,

    /** A heavy click: a landing, a wall. */
    THUD,

    /** About 250 ms of steady buzz: an alarm, a long effect. */
    BUZZ,

    /** Two rising short pulses: a win, a level done. */
    SUCCESS,

    /** One long low pulse: a loss. */
    FAIL,
}

/** Which sensors a toy needs. */
public enum class ToySensors {
    /** No motion: the toy gets no [ToyEngine.onMotion] calls. */
    NONE,

    /** Tilt, shake and free fall; [Motion.spin] and [Motion.wobble] stay 0. */
    ACCELEROMETER,

    /** Tilt plus turning ([Motion.spin], [Motion.wobble]). Costs more battery. */
    ACCELEROMETER_AND_GYROSCOPE,
}

/**
 * One Glyph Toy as pure logic: button and motion in, 25x25 frames out. No Android inside, so the same engine runs in the
 * toy service, on the app screen and in unit tests. Time only moves through [frame]; never read a clock inside an engine.
 *
 * Hosts call every method on one thread (the main thread), so an engine needs no locking. Every [onLongPress] is
 * followed by exactly one [onRelease], also when the hold is cut short (the finger leaves the screen, the screen closes).
 */
public interface ToyEngine {
    /** Which sensors to run for this toy. */
    public val sensors: ToySensors get() = ToySensors.ACCELEROMETER

    /** Sensor readings per second the toy wants: 50 is enough for tilt, up to 200 for impacts and free fall. */
    public val sensorHz: Int get() = 50

    /** Long press of the Glyph Button. If the player keeps holding, [onRelease] follows when they let go. */
    public fun onLongPress() {}

    /** The button was let go after a long press. */
    public fun onRelease() {}

    /** A new sensor reading, already in picture axes. */
    public fun onMotion(motion: Motion) {}

    /** Advances the toy by [dtMs] and returns the next frame: 625 values 0..4095, row by row. */
    public fun frame(dtMs: Long): IntArray

    /** One calm frame for the always-on display. Must not change the state of the toy. */
    public fun still(): IntArray

    /** Haptics asked for since the last call, oldest first. The host plays them; a toy must not depend on them being felt. */
    public fun drainHaptics(): List<Haptic> = emptyList()
}
