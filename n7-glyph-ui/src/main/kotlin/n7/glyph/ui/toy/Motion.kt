package n7.glyph.ui.toy

import kotlin.math.sqrt

/** Which face of the phone the picture is on: the Glyph Matrix on the back, or the app's on-screen preview. */
public enum class Facing {
    /** The Glyph Matrix, seen by someone looking at the back of the phone. */
    BACK,

    /** The app screen in its natural (portrait) orientation. */
    SCREEN,
}

/**
 * The phone's movement at one instant, in the frame of the picture as its viewer sees it:
 * x to the viewer's right, y down, z towards the viewer.
 *
 * Because it is already in picture axes, a toy written once behaves the same on the matrix and on the screen.
 */
public class Motion(
    /** When the reading was taken, ms on the sensor clock (monotonic, not wall time). */
    public val timeMs: Long,
    /** Where things fall, m/s2. Phone upright and at rest: (0, 9.8, 0). Picture facing the ceiling, viewer above: (0, 0, -9.8). */
    public val downX: Float,
    /** See [downX]. */
    public val downY: Float,
    /** See [downX]. */
    public val downZ: Float,
    /** Turning around the viewing axis, rad/s, positive = clockwise as the viewer sees the picture. 0 without a gyroscope. */
    public val spin: Float = 0f,
    /** Turning around the two other axes, rad/s, always >= 0 (how much the picture tips and swings). 0 without a gyroscope. */
    public val wobble: Float = 0f,
) {
    /** Length of the down vector, m/s2: about 9.8 at rest, near 0 in free fall, large in a shake. */
    public val force: Float get() = sqrt(downX * downX + downY * downY + downZ * downZ)

    override fun toString(): String =
        "Motion(t=$timeMs, down=($downX, $downY, $downZ), spin=$spin, wobble=$wobble)"

    /** Gravity and constructors. */
    public companion object {
        /** Standard gravity, m/s2. */
        public const val G: Float = 9.80665f

        /**
         * From raw device-axis values (accelerometer m/s2, gyroscope rad/s) as Android reports them.
         *
         * Android device axes, looking at the screen in portrait: +x right, +y up, +z out of the screen. The
         * accelerometer reports the reaction force, so gravity in device axes is (-ax, -ay, -az). Seen from the screen
         * the viewer's axes are (+x, -y, +z); seen from the back, where the phone is turned around its y axis, they are
         * (-x, -y, -z). A positive gz turns the phone counter-clockwise as seen from the screen, which is clockwise as
         * seen from the back.
         */
        public fun fromDevice(
            timeMs: Long,
            ax: Float,
            ay: Float,
            az: Float,
            gx: Float,
            gy: Float,
            gz: Float,
            facing: Facing,
        ): Motion {
            val wobble = sqrt(gx * gx + gy * gy)
            return when (facing) {
                Facing.SCREEN -> Motion(timeMs, downX = -ax, downY = ay, downZ = -az, spin = -gz, wobble = wobble)
                Facing.BACK -> Motion(timeMs, downX = ax, downY = ay, downZ = az, spin = gz, wobble = wobble)
            }
        }

        /** A phone held still and upright. Handy in tests. */
        public fun rest(timeMs: Long = 0L): Motion = Motion(timeMs, 0f, G, 0f)
    }
}
