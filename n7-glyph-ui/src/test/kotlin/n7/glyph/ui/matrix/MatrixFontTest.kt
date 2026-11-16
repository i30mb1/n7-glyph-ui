package n7.glyph.ui.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixFontTest {

    private val smallChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ .,:!?-+%/'"
    private val bigChars = "0123456789 .:-"

    @Test
    fun smallHasEveryPromisedCharacter() {
        val font = MatrixFont.Small
        assertEquals(5, font.height)
        assertEquals(smallChars.toSet(), font.chars)
        for (c in smallChars) assertGlyph(font, c, if (c.isLetterOrDigit() || c == ' ') 3 else null)
        // Lowercase is drawn as uppercase.
        for (c in 'a'..'z') assertEquals(font.glyph(c.uppercaseChar()), font.glyph(c))
        assertNull(font.glyph('@'))
        assertNull(font.glyph('Ж'))
    }

    @Test
    fun bigHasEveryPromisedCharacter() {
        val font = MatrixFont.Big
        assertEquals(7, font.height)
        assertEquals(bigChars.toSet(), font.chars)
        for (c in bigChars) assertGlyph(font, c, if (c.isDigit() || c == ' ') 5 else null)
        assertNull(font.glyph('A'))
        assertNull(font.glyph('a'))
    }

    private fun assertGlyph(font: MatrixFont, c: Char, fullWidth: Int?) {
        assertNotNull("'$c'", font.glyph(c))
        val g = font.glyph(c)!!
        assertEquals("'$c' rows", font.height, g.size)
        assertTrue("'$c' rectangle", g.all { it.length == g[0].length })
        assertTrue("'$c' only '#' and ' '", g.all { row -> row.all { it == '#' || it == ' ' } })
        if (fullWidth != null) assertEquals("'$c' width", fullWidth, g[0].length)
        if (c != ' ') assertTrue("'$c' is not blank", g.any { '#' in it })
        // No glyph wastes a fully dark column at its sides, except digits/letters kept at full width.
        if (fullWidth == null && c != ' ') {
            assertTrue("'$c' left column used", g.any { it.first() == '#' })
            assertTrue("'$c' right column used", g.any { it.last() == '#' })
        }
    }

    @Test
    fun widthCountsGapsButNoTrailingGap() {
        val s = MatrixFont.Small
        assertEquals(0, s.width(""))
        assertEquals(3, s.width("A"))
        assertEquals(7, s.width("AB"))
        assertEquals(1, s.width("."))
        assertEquals(5, s.width("A."))
        assertEquals(3 + 1 + 1 + 1 + 3, s.width("1:2"))
        assertEquals(3 + 1 + 3 + 1 + 3, s.width("A A"))
        // Unknown characters take the room of a space; lowercase that of uppercase.
        assertEquals(s.width("A A"), s.width("AéA"))
        assertEquals(s.width("TILT"), s.width("tilt"))
        val b = MatrixFont.Big
        assertEquals(23, b.width("1234"))
        assertEquals(25, b.width("12:34"))
        assertEquals(5 + 1 + 3 + 1 + 5, b.width("1-2"))
        // Digits never change the width of a number.
        assertEquals(b.width("0000"), b.width("1111"))
        assertEquals(s.width("000"), s.width("111"))
    }

    @Test
    fun printEveryGlyph() {
        for ((name, font, chars) in listOf(Triple("Small", MatrixFont.Small, smallChars), Triple("Big", MatrixFont.Big, bigChars))) {
            println("== $name (${font.height} rows) ==")
            // Ten glyphs per line, each framed so its width is visible.
            for (chunk in chars.chunked(10)) {
                val glyphs = chunk.map { font.glyph(it)!! }
                println(chunk.toList().zip(glyphs).joinToString("  ") { (c, g) -> "'$c'".padEnd(g[0].length + 2) })
                for (row in 0 until font.height) {
                    println(glyphs.joinToString("  ") { g -> "|" + g[row].replace(' ', '.').replace('#', '@') + "|" })
                }
                println()
            }
        }
        // Sample words as they look on the matrix.
        for (word in listOf("TILT. EAT. GROW.", "HOLD!", "100%", "3/4", "WIN?", "GO-GO", "IT'S", "MAX, MIN", "QUICK", "JUMP", "BOX VIEW", "KZ NM WX")) {
            val f = MatrixFont.Small
            val rows = Array(f.height) { StringBuilder() }
            for ((i, c) in word.withIndex()) {
                if (i > 0) rows.forEach { it.append(' ') }
                val g = f.glyph(c)
                for (r in 0 until f.height) rows[r].append(g?.get(r) ?: " ".repeat(3))
            }
            println(word)
            rows.forEach { println(it.toString().replace(' ', '.').replace('#', '@')) }
            println()
        }
        for (number in listOf("0123", "4567", "89:01", "12.5", "-7")) {
            val f = MatrixFont.Big
            val rows = Array(f.height) { StringBuilder() }
            for ((i, c) in number.withIndex()) {
                if (i > 0) rows.forEach { it.append(' ') }
                val g = f.glyph(c)!!
                for (r in 0 until f.height) rows[r].append(g[r])
            }
            println(number)
            rows.forEach { println(it.toString().replace(' ', '.').replace('#', '@')) }
            println()
        }
    }
}
