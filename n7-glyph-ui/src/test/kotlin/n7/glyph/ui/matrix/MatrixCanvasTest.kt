package n7.glyph.ui.matrix

import n7.glyph.ui.matrix.MatrixCanvas.Companion.MAX
import n7.glyph.ui.matrix.MatrixCanvas.Companion.RIM
import n7.glyph.ui.matrix.MatrixCanvas.Companion.SIZE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

class MatrixCanvasTest {

    private fun lit(c: MatrixCanvas): Set<Pair<Int, Int>> = buildSet {
        for (y in 0 until SIZE) for (x in 0 until SIZE) if (c[x, y] > 0) add(x to y)
    }

    private fun picture(c: MatrixCanvas): String = buildString {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val v = c[x, y]
                append(
                    when {
                        !GlyphMatrixSpec.isVisible(x, y) -> ' '
                        v == 0 -> '.'
                        v >= MAX -> '#'
                        else -> '+'
                    },
                )
            }
            append('\n')
        }
    }

    private fun neighbours(a: Pair<Int, Int>, b: Pair<Int, Int>) =
        max(abs(a.first - b.first), abs(a.second - b.second)) == 1

    // Every lit cell of a path has an 8-neighbour that is lit too, and the lit set is one connected piece.
    private fun assertConnected(cells: Set<Pair<Int, Int>>) {
        if (cells.isEmpty()) return
        val seen = HashSet<Pair<Int, Int>>()
        val todo = ArrayDeque(listOf(cells.first()))
        while (todo.isNotEmpty()) {
            val p = todo.removeFirst()
            if (!seen.add(p)) continue
            for (dx in -1..1) for (dy in -1..1) {
                val q = p.first + dx to p.second + dy
                if (q in cells && q !in seen) todo += q
            }
        }
        assertEquals("not one connected piece", cells, seen)
    }

    @Test
    fun clipsEverythingOutsideTheOutline() {
        val c = MatrixCanvas()
        c[0, 0] = MAX
        c[24, 24] = MAX
        c[-1, 12] = MAX
        c[25, 12] = MAX
        c[12, -1] = MAX
        c[12, 25] = MAX
        c.add(8, 0, 100)
        c.lighten(16, 0, 100)
        assertTrue(c.pixels.all { it == 0 })
        assertEquals(0, c[0, 0])
        assertEquals(0, c[-5, -5])
        assertEquals(0, c[100, 3])
        // The corners of the visible rows are real LEDs.
        c[9, 0] = 7
        c[15, 0] = 8
        c[0, 12] = 9
        assertEquals(7, c[9, 0])
        assertEquals(8, c[15, 0])
        assertEquals(9, c[0, 12])
    }

    @Test
    fun setClampsAddClampsLightenKeepsBrighter() {
        val c = MatrixCanvas()
        c[12, 12] = 99999
        assertEquals(MAX, c[12, 12])
        c[12, 12] = -5
        assertEquals(0, c[12, 12])
        c.add(12, 12, 3000)
        c.add(12, 12, 3000)
        assertEquals(MAX, c[12, 12])
        c.add(12, 12, -5000)
        assertEquals(0, c[12, 12])
        c.add(12, 12, Int.MAX_VALUE)
        c.add(12, 12, Int.MAX_VALUE)
        assertEquals(MAX, c[12, 12])
        c[1, 12] = 1000
        c.lighten(1, 12, 500)
        assertEquals(1000, c[1, 12])
        c.lighten(1, 12, 2000)
        assertEquals(2000, c[1, 12])
        c.lighten(1, 12, 99999)
        assertEquals(MAX, c[1, 12])
    }

    @Test
    fun fillLightsExactlyTheVisibleLeds() {
        val c = MatrixCanvas()
        c.fill(1234)
        var count = 0
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val v = c.pixels[y * SIZE + x]
            if (GlyphMatrixSpec.isVisible(x, y)) { assertEquals(1234, v); count++ } else assertEquals(0, v)
        }
        assertEquals(489, count)
        assertTrue(c.clear().pixels.all { it == 0 })
    }

    @Test
    fun lineHitsBothEndsInEveryDirection() {
        val ends = listOf(2 to 12, 22 to 12, 12 to 2, 12 to 22, 5 to 5, 19 to 19, 5 to 19, 19 to 5, 3 to 9, 20 to 14, 8 to 21, 16 to 3)
        for (a in ends) for (b in ends) {
            val c = MatrixCanvas()
            c.line(a.first, a.second, b.first, b.second, MAX)
            val lit = lit(c)
            assertTrue("$a-$b start", a in lit)
            assertTrue("$a-$b end", b in lit)
            val steps = max(abs(a.first - b.first), abs(a.second - b.second))
            assertEquals("$a-$b length", steps + 1, lit.size)
            assertConnected(lit)
            // Same cells drawn the other way round (Bresenham is symmetric up to ties; allow none here except ties).
            val back = MatrixCanvas().also { it.line(b.first, b.second, a.first, a.second, MAX) }
            assertEquals("$a-$b reverse length", lit.size, lit(back).size)
        }
    }

    @Test
    fun lineIsStraightAndLightens() {
        val c = MatrixCanvas()
        c.line(3, 12, 21, 12, 1000)
        assertEquals((3..21).map { it to 12 }.toSet(), lit(c))
        c.line(12, 3, 12, 21, 500)
        assertEquals(1000, c[12, 12])
        c.line(12, 3, 12, 21, 3000)
        assertEquals(3000, c[12, 12])
        // A diagonal is exact.
        val d = MatrixCanvas().also { it.line(4, 4, 20, 20, MAX) }
        assertEquals((4..20).map { it to it }.toSet(), lit(d))
        // Off-screen parts are clipped, the rest is drawn.
        val e = MatrixCanvas().also { it.line(-10, 12, 40, 12, MAX) }
        assertEquals((0..24).map { it to 12 }.toSet(), lit(e))
    }

    @Test
    fun rectOutlineAndFill() {
        val c = MatrixCanvas()
        c.rect(8, 8, 9, 7, MAX)
        val lit = lit(c)
        assertEquals(2 * 9 + 2 * 7 - 4, lit.size)
        assertTrue((8 to 8) in lit && (16 to 8) in lit && (8 to 14) in lit && (16 to 14) in lit)
        assertFalse((12 to 11) in lit)
        assertEquals(1, lit(MatrixCanvas().also { it.rect(12, 12, 1, 1, MAX) }).size)
        assertEquals(5, lit(MatrixCanvas().also { it.rect(10, 12, 5, 1, MAX) }).size)
        assertTrue(lit(MatrixCanvas().also { it.rect(10, 12, 0, 5, MAX); it.rect(10, 12, 5, -1, MAX) }).isEmpty())

        val f = MatrixCanvas()
        f.fill(MAX)
        f.fillRect(10, 10, 5, 4, 0)
        val dark = (0 until SIZE).flatMap { y -> (0 until SIZE).map { it to y } }.filter { GlyphMatrixSpec.isVisible(it.first, it.second) && f[it.first, it.second] == 0 }
        assertEquals(20, dark.size)
        assertTrue(dark.all { it.first in 10..14 && it.second in 10..13 })
        // Clipped at the edges, no crash.
        MatrixCanvas().fillRect(-5, -5, 50, 50, MAX)
    }

    @Test
    fun circleIsAThinClosedRingAtTheRightDistance() {
        for (r in listOf(1f, 2f, 3.5f, 5f, 8f, 11f)) {
            val c = MatrixCanvas()
            c.circle(12f, 12f, r, MAX)
            val lit = lit(c)
            assertTrue("r=$r empty", lit.isNotEmpty())
            for ((x, y) in lit) assertTrue("r=$r ($x,$y)", abs(hypot(x - 12.0, y - 12.0) - r) <= 0.5)
            assertConnected(lit)
            // Symmetric under both mirrors and the transpose.
            for ((x, y) in lit) {
                assertTrue((24 - x to y) in lit)
                assertTrue((x to 24 - y) in lit)
                assertTrue((y to x) in lit)
            }
            // The centre stays dark, so it is a ring, not a blob.
            assertFalse((12 to 12) in lit)
            println("circle r=$r\n${picture(c)}")
        }
        // Radius 0 lights the centre LED only.
        assertEquals(setOf(12 to 12), lit(MatrixCanvas().also { it.circle(12f, 12f, 0f, MAX) }))
    }

    @Test
    fun discCoversItsCircleAndIsFilled() {
        for (r in listOf(0f, 1f, 2.5f, 4f, 7f, 12f)) {
            val ring = lit(MatrixCanvas().also { it.circle(12f, 12f, r, MAX) })
            val disc = lit(MatrixCanvas().also { it.disc(12f, 12f, r, MAX) })
            assertTrue("r=$r", disc.containsAll(ring))
            for ((x, y) in disc) assertTrue(hypot(x - 12.0, y - 12.0) < r + 0.5)
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                if (GlyphMatrixSpec.isVisible(x, y) && hypot(x - 12.0, y - 12.0) < r - 0.5) assertTrue((x to y) in disc)
            }
        }
        // Overwrites: a dark disc cuts a hole.
        val c = MatrixCanvas()
        c.fill(MAX)
        c.disc(12f, 12f, 3f, 0)
        assertEquals(0, c[12, 12])
        assertEquals(MAX, c[12, 4])
    }

    @Test
    fun arcStartsAtTwelveAndRunsClockwise() {
        val c = MatrixCanvas()
        c.arc(12f, 12f, 10f, 0f, 90f, MAX)
        val lit = lit(c)
        assertTrue((12 to 2) in lit)   // 12 o'clock
        assertTrue((22 to 12) in lit)  // 3 o'clock
        assertFalse((2 to 12) in lit)  // 9 o'clock
        assertFalse((12 to 22) in lit) // 6 o'clock
        for ((x, y) in lit) assertTrue("($x,$y) in the top-right quarter", x >= 12 && y <= 12)
        assertConnected(lit)
        println("arc 0..90\n${picture(c)}")

        // A negative sweep goes counter-clockwise: the top-left quarter.
        val ccw = lit(MatrixCanvas().also { it.arc(12f, 12f, 10f, 0f, -90f, MAX) })
        for ((x, y) in ccw) assertTrue("($x,$y) in the top-left quarter", x <= 12 && y <= 12)
        assertTrue((2 to 12) in ccw && (12 to 2) in ccw)

        // Starting at 3 o'clock for half a turn: the bottom half.
        val half = lit(MatrixCanvas().also { it.arc(12f, 12f, 10f, 90f, 180f, MAX) })
        for ((x, y) in half) assertTrue(y >= 12)
        assertTrue((12 to 22) in half)

        // Wrapping past 360 and a full sweep.
        val wrap = lit(MatrixCanvas().also { it.arc(12f, 12f, 10f, 315f, 90f, MAX) })
        for ((x, y) in wrap) assertTrue(y <= 12)
        assertTrue((12 to 2) in wrap)
        val full = lit(MatrixCanvas().also { it.arc(12f, 12f, 10f, 123f, 360f, MAX) })
        assertEquals(lit(MatrixCanvas().also { it.circle(12f, 12f, 10f, MAX) }), full)
        // Two halves make the whole circle.
        val a = MatrixCanvas().also { it.arc(12f, 12f, 10f, 0f, 180f, MAX); it.arc(12f, 12f, 10f, 180f, 180f, MAX) }
        assertEquals(full, lit(a))
        assertTrue(lit(MatrixCanvas().also { it.arc(12f, 12f, 10f, 0f, 0f, MAX) }).isEmpty())
    }

    @Test
    fun rimIsOneClosedClockwiseLoopFromTwelve() {
        assertEquals(68, RIM.size)
        assertEquals(68, RIM.toSet().size)
        assertEquals(12 to 0, RIM.first())
        for (i in RIM.indices) {
            val a = RIM[i]
            val b = RIM[(i + 1) % RIM.size]
            assertTrue("$a -> $b", neighbours(a, b))
        }
        // On the edge: visible, with an invisible 4-neighbour.
        for ((x, y) in RIM) {
            assertTrue(GlyphMatrixSpec.isVisible(x, y))
            assertFalse(
                GlyphMatrixSpec.isVisible(x - 1, y) && GlyphMatrixSpec.isVisible(x + 1, y) &&
                    GlyphMatrixSpec.isVisible(x, y - 1) && GlyphMatrixSpec.isVisible(x, y + 1),
            )
        }
        // Clockwise as seen on the matrix (y down): the angle from 12 o'clock never goes back.
        val angles = RIM.map { (x, y) ->
            val a = Math.toDegrees(atan2(x - 12.0, -(y - 12.0)))
            if (a < 0) a + 360 else a
        }
        assertEquals(angles.sorted(), angles)
        // A quarter of the loop lands on 3, 6 and 9 o'clock.
        assertEquals(24 to 12, RIM[17])
        assertEquals(12 to 24, RIM[34])
        assertEquals(0 to 12, RIM[51])
    }

    @Test
    fun rimLightsTheRightNumberFromTheStart() {
        val c = MatrixCanvas()
        c.rim(0.5f, MAX)
        val lit = lit(c)
        assertEquals(34, lit.size)
        assertEquals(RIM.take(34).toSet(), lit)
        assertEquals(68, lit(MatrixCanvas().also { it.rim(1f, MAX) }).size)
        assertEquals(68, lit(MatrixCanvas().also { it.rim(7f, MAX) }).size)
        assertTrue(lit(MatrixCanvas().also { it.rim(0f, MAX); it.rim(-1f, MAX) }).isEmpty())
        // From another start, wrapping, and negative starts.
        assertEquals(setOf(RIM[66], RIM[67], RIM[0], RIM[1]), lit(MatrixCanvas().also { it.rim(4f / 68, MAX, startIndex = 66) }))
        assertEquals(setOf(RIM[67], RIM[0]), lit(MatrixCanvas().also { it.rim(2f / 68, MAX, startIndex = -1) }))
        assertEquals(setOf(RIM[1]), lit(MatrixCanvas().also { it.rim(1f / 68, MAX, startIndex = 69) }))
        println("rim 0.5\n${picture(c)}")
    }

    @Test
    fun spriteLevelsAndTransparency() {
        val c = MatrixCanvas()
        c.fill(100)
        c.sprite(listOf("#+. x", " #"), 10, 12, 4000)
        assertEquals(4000, c[10, 12])
        assertEquals(2400, c[11, 12])
        assertEquals(1000, c[12, 12])
        assertEquals(100, c[13, 12]) // ' ' transparent
        assertEquals(100, c[14, 12]) // 'x' transparent
        assertEquals(100, c[10, 13])
        assertEquals(4000, c[11, 13])
        // Clipped, not crashing.
        c.sprite(listOf("#####"), -2, 0)
        assertEquals(0, c[0, 0])
    }

    @Test
    fun textAdvancesAndCentres() {
        val c = MatrixCanvas()
        val end = c.text("12", 3, 10)
        assertEquals(3 + MatrixFont.Small.width("12"), end)
        assertEquals(10, end)
        // '1' top row is ".#." at x 3..5, '2' starts after a gap at x 7.
        assertEquals(0, c[3, 10])
        assertEquals(MAX, c[4, 10])
        assertEquals(MAX, c[7, 10])
        assertEquals(0, c[6, 10])
        assertEquals(3, c.text("", 3, 0))
        // Unknown characters leave a space.
        val d = MatrixCanvas()
        assertEquals(2 + MatrixFont.Small.width("A"), d.text("A", 2, 10))
        assertEquals(2 + MatrixFont.Small.width("A A"), MatrixCanvas().text("AéA", 2, 10))
        assertEquals(lit(MatrixCanvas().also { it.text("A A", 2, 10) }), lit(MatrixCanvas().also { it.text("AéA", 2, 10) }))
        // Lowercase is drawn as uppercase.
        assertEquals(lit(MatrixCanvas().also { it.text("GO", 5, 10) }), lit(MatrixCanvas().also { it.text("go", 5, 10) }))

        // Odd width: symmetric around column 12.
        val e = MatrixCanvas().also { it.textCentered("8", 10) }
        assertEquals(setOf(11, 12, 13), lit(e).map { it.first }.toSet())
        val f = MatrixCanvas().also { it.textCentered("888", 10) } // width 11: 7..17
        assertEquals(7, lit(f).minOf { it.first })
        assertEquals(17, lit(f).maxOf { it.first })
        val g = MatrixCanvas().also { it.textCentered("8888", 9, font = MatrixFont.Big) } // width 23: 1..23
        assertEquals(1, lit(g).minOf { it.first })
        assertEquals(23, lit(g).maxOf { it.first })
        println("text\n${picture(MatrixCanvas().also { it.textCentered("TILT.", 4); it.textCentered("42", 9, font = MatrixFont.Big); it.textCentered("EAT ME", 17) })}")
    }

    @Test
    fun frameIsACopyAndCellsMatch() {
        val c = MatrixCanvas()
        c[12, 12] = MAX
        c[3, 12] = 1
        val f = c.frame()
        assertNotSame(c.pixels, f)
        assertArrayEquals(c.pixels, f)
        f[0] = 5
        assertEquals(0, c.pixels[0])
        val cells = c.cells()
        assertEquals(2, cells.size)
        assertEquals(Cell(12, 12, 1f), cells.first { it.x == 12 })
        assertEquals(0.12f, cells.first { it.x == 3 }.level, 1e-6f)
    }

    @Test
    fun cellsAreMonotonicAndPerceptual() {
        var previous = 0f
        for (v in 1..MAX) {
            val frame = IntArray(SIZE * SIZE)
            frame[12 * SIZE + 12] = v
            val level = MatrixCanvas.cells(frame).single().level
            assertTrue("v=$v", level >= previous)
            assertTrue(level in 0.12f..1f)
            previous = level
        }
        assertEquals(1f, previous, 1e-6f)
        val half = IntArray(SIZE * SIZE).also { it[12 * SIZE + 12] = MAX / 2 }
        assertEquals(Math.pow(0.5, 0.45).toFloat(), MatrixCanvas.cells(half).single().level, 0.001f)
        // Dark LEDs, LEDs outside the outline and short frames give nothing; values above MAX clamp.
        val odd = IntArray(SIZE * SIZE).also { it[0] = MAX; it[12 * SIZE + 12] = 0; it[12 * SIZE + 13] = -7; it[12 * SIZE + 14] = 99999 }
        assertEquals(listOf(Cell(14, 12, 1f)), MatrixCanvas.cells(odd))
        assertTrue(MatrixCanvas.cells(IntArray(0)).isEmpty())
    }
}
