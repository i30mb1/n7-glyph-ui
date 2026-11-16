package n7.glyph.ui.matrix

import n7.glyph.ui.matrix.MatrixCanvas.Companion.MAX
import n7.glyph.ui.matrix.MatrixCanvas.Companion.RIM
import n7.glyph.ui.matrix.MatrixCanvas.Companion.SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.roundToInt

/** The primitives at the edge of the round outline, far off the canvas and with odd arguments. */
class MatrixCanvasEdgeTest {

    private fun lit(c: MatrixCanvas): Set<Pair<Int, Int>> = buildSet {
        for (y in 0 until SIZE) for (x in 0 until SIZE) if (c[x, y] > 0) add(x to y)
    }

    private fun visible(): Set<Pair<Int, Int>> = buildSet {
        for (y in 0 until SIZE) for (x in GlyphMatrixSpec.rowRange(y)) add(x to y)
    }

    private fun canvas(draw: MatrixCanvas.() -> Unit) = MatrixCanvas().apply(draw)

    @Test
    fun nothingEverLandsOutsideTheOutline() {
        val c = canvas {
            circle(12f, 12f, 12f, MAX)
            circle(12f, 12f, 13f, MAX)
            disc(0f, 0f, 6f, MAX)
            arc(24f, 24f, 5f, 0f, 360f, MAX)
            line(-3, -3, 27, 27, MAX)
            rect(-1, -1, 27, 27, MAX)
            fillRect(20, 20, 10, 10, MAX)
            sprite(listOf("#####", "#####"), -2, -1)
            text("8888888", 15, 20)
        }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (!GlyphMatrixSpec.isVisible(x, y)) assertEquals("($x,$y)", 0, c.pixels[y * SIZE + x])
        }
    }

    @Test
    fun aCircleOnTheOutlineKeepsItsFourExtremes() {
        val l = lit(canvas { circle(12f, 12f, 12f, MAX) })
        for (p in listOf(12 to 0, 0 to 12, 24 to 12, 12 to 24)) assertTrue("$p", p in l)
        assertTrue(l.all { (x, y) -> GlyphMatrixSpec.isVisible(x, y) })
    }

    @Test
    fun shapesFarAwayOrDegenerateDrawNothingAndNeverThrow() {
        val c = canvas {
            circle(-50f, -50f, 5f, MAX)
            disc(1e9f, 1e9f, 3f, MAX)
            disc(-1e9f, 12f, 3f, MAX)
            arc(100f, 12f, 4f, 0f, 90f, MAX)
            circle(12f, 12f, -1f, MAX)
            disc(12f, 12f, -0.1f, MAX)
            circle(Float.NaN, 12f, 3f, MAX)
            disc(12f, 12f, Float.NaN, MAX)
            arc(12f, 12f, 5f, Float.NaN, 90f, MAX)
            arc(12f, 12f, 5f, 0f, Float.NaN, MAX)
            circle(12f, 12f, Float.POSITIVE_INFINITY, MAX)
            rect(3, 3, -4, 2, MAX)
            fillRect(3, 3, -4, 2, MAX)
            fillRect(Int.MIN_VALUE, Int.MIN_VALUE, 10, 10, MAX)
            fillRect(Int.MAX_VALUE, Int.MAX_VALUE, 10, 10, MAX)
            sprite(listOf("###"), Int.MAX_VALUE - 1, 12)
            sprite(listOf("###"), Int.MIN_VALUE, 12)
            set(Int.MIN_VALUE, Int.MAX_VALUE, MAX)
            add(Int.MAX_VALUE, Int.MIN_VALUE, MAX)
            lighten(Int.MIN_VALUE, Int.MIN_VALUE, MAX)
        }
        assertTrue(lit(c).isEmpty())
        assertEquals(0, c[Int.MIN_VALUE, Int.MIN_VALUE])
        assertEquals(0, c[Int.MAX_VALUE, 12])
    }

    @Test
    fun aHugeDiscFillsTheWholeMatrix() {
        assertEquals(visible(), lit(canvas { disc(12f, 12f, Float.MAX_VALUE, MAX) }))
        assertEquals(visible(), lit(canvas { disc(-1000f, 12f, 2000f, MAX) }))
        assertEquals(visible(), lit(canvas { fillRect(-5, -5, 50, 50, MAX) }))
    }

    @Test
    fun aLineFromFarOffTheCanvasIsClippedNotBent() {
        val far = lit(canvas { line(-1000, -1000, 1000, 1000, MAX) })
        assertEquals(lit(canvas { line(0, 0, 24, 24, MAX) }), far)
        val steep = lit(canvas { line(12, -500, 12, 500, MAX) })
        assertEquals((0..24).map { 12 to it }.toSet(), steep)
        // A line that never crosses the outline lights nothing.
        assertTrue(lit(canvas { line(-10, -10, -10, 40, MAX); line(0, 0, 3, 0, MAX) }).isEmpty())
    }

    // Runs [block] on a daemon thread, so a walk that never ends fails the test instead of hanging the build.
    private fun finishesWithin(ms: Long, block: () -> Unit) {
        var error: Throwable? = null
        val t = Thread { try { block() } catch (e: Throwable) { error = e } }.apply { isDaemon = true; start() }
        t.join(ms)
        assertFalse("still running after $ms ms", t.isAlive)
        error?.let { throw it }
    }

    @Test
    fun aLineWithHugeEndsIsQuickAndStraight() {
        val row12 = lit(canvas { line(12, 12, 40, 12, MAX) })
        val infinite = (12 + 1f / 0f).toInt()
        for ((x1, y1) in listOf(Int.MAX_VALUE to 12, 1_200_000_000 to 12, 200_000_000 to 12, infinite to 12)) {
            finishesWithin(1000) { assertEquals("to ($x1,$y1)", row12, lit(canvas { line(12, 12, x1, y1, MAX) })) }
        }
        finishesWithin(1000) {
            assertEquals(lit(canvas { line(12, 12, 12, -40, MAX) }), lit(canvas { line(12, 12, 12, Int.MIN_VALUE, MAX) }))
            assertEquals((0..24).map { it to 12 }.toSet(), lit(canvas { line(Int.MIN_VALUE, 12, Int.MAX_VALUE, 12, MAX) }))
            assertEquals((0..24).map { 3 to it }.toSet() intersect visible(), lit(canvas { line(3, -2_000_000_000, 3, 2_000_000_000, MAX) }))
            assertEquals(lit(canvas { line(0, 0, 24, 24, MAX) }), lit(canvas { line(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }))
            // A huge line that passes the matrix by lights nothing.
            assertTrue(lit(canvas { line(-2_000_000_000, -5, 2_000_000_000, -5, MAX); line(Int.MIN_VALUE, 0, 0, Int.MIN_VALUE, MAX) }).isEmpty())
            // A slanted one stays within half an LED or so of the true line y = x / 2.
            val slant = lit(canvas { line(0, 0, 1_000_000_000, 500_000_000, MAX) })
            assertTrue(slant.isNotEmpty())
            for ((x, y) in slant) assertTrue("($x,$y)", kotlin.math.abs(y - x / 2.0) <= 1.0)
        }
    }

    @Test
    fun rectanglesReachingFarOffTheCanvasKeepEveryVisibleEdge() {
        finishesWithin(1000) {
            assertEquals(lit(canvas { fillRect(5, 5, 50, 50, MAX) }), lit(canvas { fillRect(5, 5, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }))
            assertEquals(MAX, canvas { fillRect(5, 5, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }[12, 12])
            assertEquals(visible(), lit(canvas { fillRect(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, MAX); fillRect(-1, -1, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }))
            assertEquals(lit(canvas { rect(5, 5, 50, 3, MAX) }), lit(canvas { rect(5, 5, Int.MAX_VALUE, 3, MAX) }))
            assertEquals(lit(canvas { rect(5, 5, 3, 50, MAX) }), lit(canvas { rect(5, 5, 3, Int.MAX_VALUE, MAX) }))
            assertEquals(lit(canvas { rect(-5, 5, 50, 3, MAX) }), lit(canvas { rect(-5, 5, 1_000_000_000, 3, MAX) }))
            assertEquals(lit(canvas { rect(-5, 5, 50, 3, MAX) }), lit(canvas { rect(-1_500_000_000, 5, 2_000_000_000, 3, MAX) }))
            assertEquals(lit(canvas { rect(-5, -5, 50, 50, MAX) }), lit(canvas { rect(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }))
            assertTrue(lit(canvas { rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, MAX) }).isEmpty())
        }
    }

    @Test
    fun rimWithANanFractionOrAHugeStartStillWorks() {
        assertTrue(lit(canvas { rim(0f / 0f, MAX) }).isEmpty())
        val n = Int.MAX_VALUE.mod(68)
        assertEquals(setOf(RIM[n], RIM[(n + 1) % 68], RIM[(n + 2) % 68]), lit(canvas { rim(3f / 68, MAX, startIndex = Int.MAX_VALUE) }))
        val m = Int.MIN_VALUE.mod(68)
        assertEquals(setOf(RIM[m], RIM[(m + 1) % 68]), lit(canvas { rim(2f / 68, MAX, startIndex = Int.MIN_VALUE) }))
    }

    @Test
    fun arcsOfOneCircleAddUpToTheCircle() {
        for (r in listOf(2f, 3f, 5.5f, 8f, 10f, 11f, 12f)) {
            for ((cx, cy) in listOf(12f to 12f, 11.5f to 12.5f, 6f to 18f)) {
                val circle = lit(canvas { circle(cx, cy, r, MAX) })
                for ((start, step, n) in listOf(Triple(0f, 30f, 12), Triple(22.5f, 45f, 8), Triple(-90f, 90f, 4), Triple(10f, 7.2f, 50))) {
                    val pieces = canvas { for (k in 0 until n) arc(cx, cy, r, start + k * step, step, MAX) }
                    assertEquals("r=$r c=($cx,$cy) start=$start step=$step", circle, lit(pieces))
                }
                // The same pieces drawn counter-clockwise.
                val back = canvas { for (k in 0 until 12) arc(cx, cy, r, 360f - k * 30f, -30f, MAX) }
                assertEquals("ccw r=$r", circle, lit(back))
            }
        }
    }

    @Test
    fun arcSweepsBeyondAFullTurnAndNegativeStartsWrap() {
        val circle = lit(canvas { circle(12f, 12f, 9f, MAX) })
        assertEquals(circle, lit(canvas { arc(12f, 12f, 9f, 45f, 720f, MAX) }))
        assertEquals(circle, lit(canvas { arc(12f, 12f, 9f, 45f, -360f, MAX) }))
        assertEquals(lit(canvas { arc(12f, 12f, 9f, 270f, 60f, MAX) }), lit(canvas { arc(12f, 12f, 9f, -90f, 60f, MAX) }))
        assertEquals(lit(canvas { arc(12f, 12f, 9f, 30f, 60f, MAX) }), lit(canvas { arc(12f, 12f, 9f, 750f, 60f, MAX) }))
    }

    @Test
    fun rimLightsRoundedCountsAsAContiguousRunForEveryFraction() {
        for (k in 0..136) {
            val f = k / 136f
            val n = (f * 68).roundToInt()
            for (start in listOf(0, 17, 67, -5)) {
                val got = lit(canvas { rim(f, MAX, start) })
                val want = (0 until n).map { RIM[(start + it).mod(68)] }.toSet()
                assertEquals("f=$f start=$start", want, got)
            }
        }
        // Lightens: a brighter LED under the rim stays.
        val c = canvas { this[12, 0] = MAX; rim(1f, 100) }
        assertEquals(MAX, c[12, 0])
        assertEquals(100, c[13, 0])
    }

    @Test
    fun rimIsTheSameLoopAsGlyphPetsRing() {
        // Glyph Pet's ChargeRenderer.RING: edge LEDs sorted by the double-precision angle from 12 o'clock.
        val v = GlyphMatrixSpec::isVisible
        val ring = buildList {
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                if (v(x, y) && !(v(x - 1, y) && v(x + 1, y) && v(x, y - 1) && v(x, y + 1))) add(x to y)
            }
        }.sortedBy { (x, y) ->
            val a = atan2((x - 12).toDouble(), -(y - 12).toDouble())
            if (a < 0) a + 2 * Math.PI else a
        }
        assertEquals(ring, RIM)
        // Mirror symmetric: step k clockwise from 12 o'clock mirrors step 68 - k.
        for (k in 1 until 68) {
            val (x, y) = RIM[k]
            assertEquals(24 - x to y, RIM[68 - k])
        }
    }

    @Test
    fun textAtTheEdgesClipsAndStillReturnsTheEnd() {
        val c = MatrixCanvas()
        assertEquals(-2 + MatrixFont.Small.width("88"), c.text("88", -2, 0))
        assertEquals(20 + MatrixFont.Small.width("HELLO"), c.text("HELLO", 20, 21))
        assertTrue(lit(c).all { (x, y) -> GlyphMatrixSpec.isVisible(x, y) })
        // 12:34 in the big font is exactly 25 wide: centred, it starts at column 0 ('1' has a dark first column).
        val t = lit(canvas { textCentered("12:34", 9, font = MatrixFont.Big) })
        assertEquals(1, t.minOf { it.first })
        assertEquals(1 + 1, lit(canvas { textCentered("12:34", 9, font = MatrixFont.Big) }).count { it == (1 to 10) || it == (2 to 9) })
        assertEquals(24, t.maxOf { it.first })
        assertTrue(t.all { it.second in 9..15 })
        // Text overwrites its lit dots only.
        val o = canvas { fill(100); text("-", 11, 10) }
        assertEquals(MAX, o[11, 12])
        assertEquals(100, o[11, 11])
    }

    @Test
    fun spriteAndTextLevelsAreClamped() {
        val c = canvas { sprite(listOf("#+."), 11, 12, 99999) }
        assertEquals(MAX, c[11, 12])
        assertEquals(MAX * 3 / 5, c[12, 12])
        assertEquals(MAX / 4, c[13, 12])
        val d = canvas { fill(500); sprite(listOf("#"), 12, 12, -7); text("1", 0, 12, level = -3) }
        assertEquals(0, d[12, 12])
        assertNotEquals(500, d[1, 12])
    }
}
