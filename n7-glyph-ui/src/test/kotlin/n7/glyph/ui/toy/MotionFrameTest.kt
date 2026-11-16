package n7.glyph.ui.toy

import n7.glyph.ui.toy.Motion.Companion.G
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * [Motion.fromDevice] against a derivation that never uses its sign table: a phone in a random orientation, the
 * readings Android would report for it, and the viewer's axes of each face written as device-axis vectors.
 */
class MotionFrameTest {

    private class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
        operator fun times(k: Double) = V(x * k, y * k, z * k)
        infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
        infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun unit(): V = this * (1.0 / sqrt(this dot this))
    }

    // Rotates [v] by [angle] around the unit [axis] (right-hand rule, Rodrigues).
    private fun rotate(v: V, axis: V, angle: Double): V =
        v * cos(angle) + (axis cross v) * sin(angle) + axis * ((axis dot v) * (1 - cos(angle)))

    /** Device axes in world coordinates (z up); a random orientation. */
    private class Pose(val x: V, val y: V, val z: V)

    private fun randomPose(rnd: Random): Pose {
        var x = V(1.0, 0.0, 0.0)
        var y = V(0.0, 1.0, 0.0)
        var z = V(0.0, 0.0, 1.0)
        repeat(3) {
            val axis = V(rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0)).unit()
            val a = rnd.nextDouble(0.0, 2 * Math.PI)
            x = rotate(x, axis, a); y = rotate(y, axis, a); z = rotate(z, axis, a)
        }
        return Pose(x, y, z)
    }

    // A world vector in device coordinates.
    private fun Pose.local(w: V) = V(w dot x, w dot y, w dot z)

    // The viewer's right, down and towards-the-viewer axes, as device-coordinate vectors.
    private fun viewer(facing: Facing): Triple<V, V, V> = when (facing) {
        Facing.SCREEN -> Triple(V(1.0, 0.0, 0.0), V(0.0, -1.0, 0.0), V(0.0, 0.0, 1.0))
        // Turned around its own y axis to show the back: right and towards flip, up stays up.
        Facing.BACK -> Triple(V(-1.0, 0.0, 0.0), V(0.0, -1.0, 0.0), V(0.0, 0.0, -1.0))
    }

    @Test
    fun matchesTheDerivationInRandomOrientations() {
        val rnd = Random(20261115)
        repeat(5000) {
            val pose = randomPose(rnd)
            val gravity = pose.local(V(0.0, 0.0, -G.toDouble()))
            val reading = gravity * -1.0 // the accelerometer reports the reaction
            val turn = V(rnd.nextDouble(-6.0, 6.0), rnd.nextDouble(-6.0, 6.0), rnd.nextDouble(-6.0, 6.0))
            for (facing in Facing.entries) {
                val (right, down, towards) = viewer(facing)
                val m = Motion.fromDevice(
                    9L, reading.x.toFloat(), reading.y.toFloat(), reading.z.toFloat(),
                    turn.x.toFloat(), turn.y.toFloat(), turn.z.toFloat(), facing,
                )
                assertEquals((gravity dot right).toFloat(), m.downX, 1e-4f)
                assertEquals((gravity dot down).toFloat(), m.downY, 1e-4f)
                assertEquals((gravity dot towards).toFloat(), m.downZ, 1e-4f)
                assertEquals(G, m.force, 1e-3f)
                // Right-hand rule: a positive turn around `towards` is counter-clockwise for the viewer at its tip.
                assertEquals((-(turn dot towards)).toFloat(), m.spin, 1e-4f)
                assertEquals(hypot(turn dot right, turn dot down).toFloat(), m.wobble, 1e-4f)
                assertTrue(m.wobble >= 0f)
            }
        }
    }

    @Test
    fun aSmallTurnWithPositiveGzMovesTheTopClockwiseOnTheBack() {
        // Turn the phone a little around its own +z axis and watch where its top edge goes in each viewer's picture.
        val top = V(0.0, 1.0, 0.0)
        val after = rotate(top, V(0.0, 0.0, 1.0), 0.05)
        for (facing in Facing.entries) {
            val (right, down, _) = viewer(facing)
            // Picture coordinates, y down: the top starts at 12 o'clock (0, -1).
            val px = after dot right
            val py = after dot down
            // Clockwise on the picture from 12 o'clock means moving to the viewer's right.
            val clockwise = px > 0 && py < 0
            val spin = Motion.fromDevice(0L, 0f, G, 0f, 0f, 0f, 1f, facing).spin
            assertEquals("$facing", clockwise, spin > 0f)
        }
    }
}
