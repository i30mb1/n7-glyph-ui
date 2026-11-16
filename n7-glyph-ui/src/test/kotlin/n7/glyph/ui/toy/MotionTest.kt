package n7.glyph.ui.toy

import n7.glyph.ui.toy.Motion.Companion.G
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * Physical cases. Android device axes, looking at the screen in portrait: +x right, +y up, +z out of the screen.
 * The accelerometer reads the reaction to gravity: +9.8 on the axis that points up.
 */
class MotionTest {

    private val eps = 1e-4f

    private fun assertDown(m: Motion, x: Float, y: Float, z: Float) {
        assertEquals("downX", x, m.downX, eps)
        assertEquals("downY", y, m.downY, eps)
        assertEquals("downZ", z, m.downZ, eps)
    }

    private fun still(ax: Float, ay: Float, az: Float, facing: Facing) = Motion.fromDevice(0L, ax, ay, az, 0f, 0f, 0f, facing)

    @Test
    fun uprightFallsDownOnBothFaces() {
        // Held upright in portrait, top edge up: the y axis points up.
        for (facing in Facing.entries) {
            val m = still(0f, G, 0f, facing)
            assertDown(m, 0f, G, 0f)
            assertEquals(G, m.force, eps)
        }
        assertDown(Motion.rest(), 0f, G, 0f)
        assertEquals(0f, Motion.rest().spin, 0f)
        assertEquals(0f, Motion.rest().wobble, 0f)
        assertEquals(42L, Motion.rest(42L).timeMs)
    }

    @Test
    fun upsideDownFallsUp() {
        for (facing in Facing.entries) assertDown(still(0f, -G, 0f, facing), 0f, -G, 0f)
    }

    @Test
    fun leftEdgeDown() {
        // Upright, then rolled so the screen's left edge is on the table: -x points down, the reading is ax = +G.
        // Looking at the screen, things fall to the viewer's left.
        assertDown(still(G, 0f, 0f, Facing.SCREEN), -G, 0f, 0f)
        // Looking at the back, the screen's left edge is on the viewer's right.
        assertDown(still(G, 0f, 0f, Facing.BACK), G, 0f, 0f)
        // And the other way round.
        assertDown(still(-G, 0f, 0f, Facing.SCREEN), G, 0f, 0f)
        assertDown(still(-G, 0f, 0f, Facing.BACK), -G, 0f, 0f)
    }

    @Test
    fun lyingScreenUp() {
        // On a table, screen up: +z points up, az = +G.
        // The screen picture faces the ceiling (viewer above): down is away from the viewer.
        assertDown(still(0f, 0f, G, Facing.SCREEN), 0f, 0f, -G)
        // The matrix faces the table: down is towards its (imaginary) viewer.
        assertDown(still(0f, 0f, G, Facing.BACK), 0f, 0f, G)
    }

    @Test
    fun lyingScreenDown() {
        // Screen down, back (matrix) up: az = -G. Now the matrix faces the ceiling.
        assertDown(still(0f, 0f, -G, Facing.BACK), 0f, 0f, -G)
        assertDown(still(0f, 0f, -G, Facing.SCREEN), 0f, 0f, G)
    }

    @Test
    fun tiltedTowardsTheViewer() {
        // Upright, top tipped 45 deg away from the screen viewer (the screen looks a bit up): y and z share the reading.
        val k = G / sqrt(2f)
        val screen = still(0f, k, k, Facing.SCREEN)
        assertDown(screen, 0f, k, -k)
        assertEquals(G, screen.force, eps)
    }

    @Test
    fun clockwiseTurnSeenFromEachFace() {
        // Turning the phone clockwise as seen from the screen is a rotation around -z: gz < 0.
        val cwFromScreen = Motion.fromDevice(0L, 0f, G, 0f, 0f, 0f, -2f, Facing.SCREEN)
        assertEquals(2f, cwFromScreen.spin, eps)
        // Seen from the back, the same turn is counter-clockwise.
        assertEquals(-2f, Motion.fromDevice(0L, 0f, G, 0f, 0f, 0f, -2f, Facing.BACK).spin, eps)
        // Clockwise as seen from the back is a rotation around +z: gz > 0.
        assertEquals(1.5f, Motion.fromDevice(0L, 0f, G, 0f, 0f, 0f, 1.5f, Facing.BACK).spin, eps)
    }

    @Test
    fun wobbleIsTheTurnAroundTheOtherAxes() {
        for (facing in Facing.entries) {
            val m = Motion.fromDevice(5L, 0f, G, 0f, 3f, -4f, 7f, facing)
            assertEquals(5f, m.wobble, eps)
            assertEquals(5L, m.timeMs)
        }
        assertEquals(0f, Motion.fromDevice(0L, 0f, G, 0f, 0f, 0f, 9f, Facing.BACK).wobble, 0f)
    }

    @Test
    fun forceShowsFreeFallAndShakes() {
        assertEquals(0f, still(0f, 0f, 0f, Facing.BACK).force, eps)
        assertEquals(5f, Motion(0L, 3f, 4f, 0f).force, eps)
        assertEquals(3 * G, still(0f, 3 * G, 0f, Facing.SCREEN).force, eps)
    }
}
