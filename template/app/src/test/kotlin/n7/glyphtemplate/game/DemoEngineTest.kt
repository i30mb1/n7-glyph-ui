package n7.glyphtemplate.game

import kotlin.math.sqrt
import kotlin.random.Random
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.matrix.MatrixCanvas
import n7.glyph.ui.toy.Haptic
import n7.glyph.ui.toy.Motion
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoEngineTest {

    private val size = MatrixCanvas.SIZE

    private fun assertValid(frame: IntArray, where: String) {
        assertEquals("$where: frame size", size * size, frame.size)
        for (y in 0 until size) for (x in 0 until size) {
            val v = frame[y * size + x]
            assertTrue("$where: ($x, $y) = $v out of 0..4095", v in 0..MatrixCanvas.MAX)
            if (!GlyphMatrixSpec.isVisible(x, y)) assertEquals("$where: ($x, $y) lit outside the round matrix", 0, v)
        }
    }

    private fun distanceFromCentre(engine: DemoEngine): Float {
        val dx = engine.ballX - DemoEngine.CENTRE
        val dy = engine.ballY - DemoEngine.CENTRE
        return sqrt(dx * dx + dy * dy)
    }

    /** Lit LEDs whose centre lies between [from] and [to] LEDs from the matrix centre. */
    private fun litBetween(frame: IntArray, from: Float, to: Float): Int {
        var n = 0
        for (y in 0 until size) for (x in 0 until size) {
            val d = sqrt(((x - 12) * (x - 12) + (y - 12) * (y - 12)).toFloat())
            if (d in from..to && frame[y * size + x] > 0) n++
        }
        return n
    }

    @Test
    fun `long random run keeps every frame valid and the ball inside`() {
        val random = Random(42)
        val engine = DemoEngine(Random(1))
        var t = 0L
        repeat(20_000) { i ->
            when (random.nextInt(40)) {
                0 -> engine.onLongPress()
                1 -> engine.onRelease()
            }
            if (random.nextInt(3) == 0) {
                val shake = if (random.nextInt(50) == 0) 60f else 12f
                engine.onMotion(Motion(t, random.nextFloat() * 2 * shake - shake, random.nextFloat() * 2 * shake - shake, random.nextFloat() * 20 - 10))
            }
            val dt = random.nextLong(0, 300)
            t += dt
            assertValid(engine.frame(dt), "frame $i")
            assertTrue("frame $i: ball at ${distanceFromCentre(engine)}", distanceFromCentre(engine) <= DemoEngine.LIMIT + 1e-3f)
            engine.drainHaptics()
        }
        assertValid(engine.still(), "still")
    }

    @Test
    fun `gravity rolls the ball the way the phone tips`() {
        // Right edge down (as the viewer sees the picture): the ball goes right of where a twin lying flat goes.
        val right = DemoEngine(Random(3))
        val flat = DemoEngine(Random(3))
        right.onMotion(Motion(0, Motion.G, 0f, 0f))
        flat.onMotion(Motion(0, 0f, 0f, -Motion.G))
        repeat(30) { right.frame(16); flat.frame(16) }
        assertTrue("x = ${right.ballX} vs ${flat.ballX}", right.ballX > flat.ballX + 3)
        assertEquals(flat.ballY, right.ballY, 1e-3f)

        // Upright: down is +y, the ball drops to the bottom of the matrix and stays there.
        val down = DemoEngine(Random(3))
        down.onMotion(Motion.rest())
        repeat(200) { down.frame(40) }
        assertTrue("y = ${down.ballY}", down.ballY > DemoEngine.CENTRE + DemoEngine.LIMIT - 0.5f)

        // Upside down: it goes to the top.
        val up = DemoEngine(Random(3))
        up.onMotion(Motion(0, 0f, -Motion.G, 0f))
        repeat(200) { up.frame(40) }
        assertTrue("y = ${up.ballY}", up.ballY < DemoEngine.CENTRE - DemoEngine.LIMIT + 0.5f)
    }

    @Test
    fun `hitting the edge ticks`() {
        val engine = DemoEngine(Random(5))
        engine.onMotion(Motion(0, Motion.G, 0f, 0f))
        val heard = mutableListOf<Haptic>()
        repeat(100) { engine.frame(16); heard += engine.drainHaptics() }
        assertTrue(heard.toString(), Haptic.TICK in heard)
    }

    @Test
    fun `long press pulses and charges the rim, release fires the ring`() {
        val engine = DemoEngine(Random(9))
        engine.onMotion(Motion(0, 0f, 0f, -Motion.G)) // lying flat: the ball only drifts
        engine.onLongPress()
        assertTrue(engine.holding)
        assertEquals(listOf(Haptic.CLICK), engine.drainHaptics())

        // The pulse leaves the centre.
        val pulse = engine.frame(200)
        assertTrue("pulse ring", engine.ringCount >= 1)
        assertTrue("pulse at radius ~3.6", litBetween(pulse, 3f, 4.5f) >= 10)

        // Held long enough, the whole rim is lit.
        var frame = pulse
        repeat(20) { frame = engine.frame(60) }
        assertEquals(1f, engine.charge, 0f)
        val rimLit = MatrixCanvas.RIM.count { (x, y) -> frame[y * size + x] > 0 }
        assertEquals(MatrixCanvas.RIM.size, rimLit)

        engine.onRelease()
        assertFalse(engine.holding)
        assertEquals(0f, engine.charge, 0f)
        assertEquals(listOf(Haptic.THUD), engine.drainHaptics())
        // 300 ms later the fired ring is about 9 LEDs out, far from the ball's few LEDs.
        frame = engine.frame(300)
        assertTrue("fired ring", engine.ringCount >= 1)
        assertTrue("ring at radius ~9", litBetween(frame, 8.5f, 9.5f) >= 30)
        // After a second it has left the matrix.
        repeat(10) { engine.frame(100) }
        assertEquals(0, engine.ringCount)
    }

    @Test
    fun `release without a long press does nothing`() {
        val a = DemoEngine(Random(11))
        val b = DemoEngine(Random(11))
        a.onRelease()
        repeat(10) { assertArrayEquals(b.frame(40), a.frame(40)) }
        assertTrue(a.drainHaptics().isEmpty())
    }

    @Test
    fun `still shows the ball in the middle and changes nothing`() {
        val a = DemoEngine(Random(7))
        val b = DemoEngine(Random(7))
        for (e in listOf(a, b)) {
            e.onMotion(Motion(0, 3f, 6f, 0f))
            repeat(10) { e.frame(40) }
            e.onLongPress()
            e.frame(300)
        }
        val still = a.still()
        assertValid(still, "still")
        assertTrue("ball in the middle", still[12 * size + 12] == MatrixCanvas.MAX)
        assertEquals("only the ball", 0, litBetween(still, 3f, 20f))
        assertArrayEquals(still, a.still())
        // A twin that never drew a still frame goes on exactly the same way.
        for (e in listOf(a, b)) e.onRelease()
        repeat(30) { assertArrayEquals(b.frame(40), a.frame(40)) }
        assertEquals(b.drainHaptics(), a.drainHaptics())
    }
}
