package n7.glyph.ui.support

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.toy.ToyEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupporterToyTest {

    private class State(override val owned: Boolean, override var thanked: Boolean = false) : SupporterState

    /** A toy that lights the centre LED and counts what it was asked to do. */
    private class Dot : ToyEngine {
        var frames = 0
        var presses = 0
        var releases = 0
        override fun frame(dtMs: Long): IntArray { frames++; return IntArray(625).also { it[12 * 25 + 12] = 1000 } }
        override fun still(): IntArray = IntArray(625).also { it[0] = 7 }
        override fun onLongPress() { presses++ }
        override fun onRelease() { releases++ }
    }

    @Test
    fun `without the purchase the toy is untouched`() {
        val dot = Dot()
        val toy = SupporterToy(dot, State(owned = false))
        repeat(50) { assertArrayEquals(Dot().frame(40), toy.frame(40)) }
        assertEquals(50, dot.frames)
    }

    @Test
    fun `after the purchase the coffee plays once, then the toy starts with sparkles`() {
        val state = State(owned = true)
        val dot = Dot()
        val toy = SupporterToy(dot, state)
        toy.frame(40)
        toy.onLongPress()
        toy.onRelease()
        assertEquals("the toy waits during the coffee", 0, dot.frames)
        assertEquals(0, dot.presses)
        assertEquals(0, dot.releases)
        var t = 40L
        while (t < SupporterGlyphs.COFFEE_MS) { toy.frame(40); t += 40 }
        assertTrue(state.thanked)
        val first = toy.frame(40)
        assertEquals(1000, first[12 * 25 + 12])
        assertTrue("sparkles on the rim", SupporterGlyphs.RIM.any { (x, y) -> first[y * 25 + x] > 0 })
        repeat(60) { toy.frame(40) }
        assertArrayEquals("sparkles end", Dot().frame(40), toy.frame(40))

        // The next session: no coffee, sparkles right away.
        val again = SupporterToy(Dot(), state).frame(40)
        assertTrue(SupporterGlyphs.RIM.any { (x, y) -> again[y * 25 + x] > 0 })
    }

    @Test
    fun `still frames always come from the toy`() {
        assertArrayEquals(Dot().still(), SupporterToy(Dot(), State(owned = true)).still())
    }

    @Test
    fun `the rim is a closed ring of real LEDs and the coffee stays on the matrix`() {
        val rim = SupporterGlyphs.RIM
        assertTrue(rim.size > 60)
        assertTrue(rim.all { (x, y) -> GlyphMatrixSpec.isVisible(x, y) })
        assertEquals(rim.size, rim.toSet().size)
        // Neighbours in the list are neighbours on the matrix: the chase runs smoothly round.
        rim.zipWithNext().forEach { (a, b) ->
            assertTrue("$a -> $b", kotlin.math.abs(a.first - b.first) <= 2 && kotlin.math.abs(a.second - b.second) <= 2)
        }
        for (t in 0L until SupporterGlyphs.COFFEE_MS step 80) {
            val f = SupporterGlyphs.coffee(t)
            for (i in f.indices) if (f[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
        assertFalse(SupporterGlyphs.coffee(1000).all { it == 0 })
    }

    @Test
    fun `the intro for frame-drawing toys plays the same opening`() {
        assertEquals(0L, SupporterIntro(State(owned = false)).durationMs)
        val state = State(owned = true)
        val first = SupporterIntro(state)
        assertEquals(SupporterGlyphs.COFFEE_MS + SupporterGlyphs.SPARKLE_MS, first.durationMs)
        assertArrayEquals(SupporterGlyphs.coffee(1000), first.frame(1000))
        assertFalse(state.thanked)
        first.frame(SupporterGlyphs.COFFEE_MS + 100)
        assertTrue(state.thanked)
        assertEquals(SupporterGlyphs.SPARKLE_MS, SupporterIntro(state).durationMs)
    }
}
