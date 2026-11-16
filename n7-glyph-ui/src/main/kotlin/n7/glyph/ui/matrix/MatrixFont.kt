package n7.glyph.ui.matrix

/**
 * A bitmap font for the 25x25 matrix, drawn with [MatrixCanvas.text].
 *
 * Glyphs are [height] LEDs tall and stand [GAP] LED apart. Digits, letters and the space all have the font's full
 * width, so a number that changes never shifts sideways and `padStart(n, ' ')` keeps numbers aligned; only
 * punctuation is narrower.
 */
public class MatrixFont internal constructor(
    /** Height of every glyph, in LEDs. */
    public val height: Int,
    private val glyphs: Map<Char, List<String>>,
) {
    // Unknown characters take this much room too.
    private val spaceWidth: Int

    init {
        for ((char, rows) in glyphs) {
            require(rows.size == height) { "Glyph '$char' must be $height rows tall" }
            require(rows[0].isNotEmpty() && rows.all { it.length == rows[0].length }) { "Glyph '$char' must be a rectangle" }
            require(rows.all { row -> row.all { it == '#' || it == ' ' } }) { "Glyph '$char' may hold only '#' and ' '" }
        }
        spaceWidth = requireNotNull(glyphs[' ']) { "A font needs a space" }[0].length
    }

    /**
     * Width of [text] in LEDs: the glyphs with [GAP] LED between them and none after the last. A character the font
     * does not have counts as a space.
     */
    public fun width(text: String): Int {
        if (text.isEmpty()) return 0
        var total = -GAP
        for (char in text) total += GAP + advance(char)
        return total
    }

    /**
     * The picture of [char]: [height] rows of equal length, '#' for a lit LED and ' ' for an unlit one, ready for
     * [MatrixCanvas.sprite]. Lowercase letters give the uppercase glyph. Null when the font has no such character.
     */
    public fun glyph(char: Char): List<String>? = glyphs[char] ?: glyphs[char.uppercaseChar()]

    /** Every character this font can draw (uppercase only; lowercase maps onto it). */
    public val chars: Set<Char> get() = glyphs.keys

    /** Width of one character's glyph; the width of a space for a character the font does not have. */
    internal fun advance(char: Char): Int = glyph(char)?.get(0)?.length ?: spaceWidth

    /** The two built-in fonts. */
    public companion object {
        /** LEDs left dark between two glyphs. */
        public const val GAP: Int = 1

        /** 3x5: digits, A-Z (lowercase is drawn as uppercase), space and . , : ! ? - + % / ' */
        public val Small: MatrixFont = MatrixFont(
            height = 5,
            glyphs = sheet(
                "0123456789",
                "### .#. ### ### #.# ### ### ### ### ###",
                "#.# ##. ..# ..# #.# #.. #.. ..# #.# #.#",
                "#.# .#. ### ### ### ### ### ..# ### ###",
                "#.# .#. #.. ..# ..# ..# #.# ..# #.# ..#",
                "### ### ### ### ..# ### ### ..# ### ###",
            ) + sheet(
                "ABCDEFGHIJKLM",
                ".#. ##. .## ##. ### ### .## #.# ### ..# #.# #.. #.#",
                "#.# #.# #.. #.# #.. #.. #.. #.# .#. ..# ##. #.. ###",
                "### ##. #.. #.# ##. ##. #.# ### .#. ..# #.. #.. ###",
                "#.# #.# #.. #.# #.. #.. #.# #.# .#. #.# ##. #.. #.#",
                "#.# ##. .## ##. ### #.. .## #.# ### .#. #.# ### #.#",
            ) + sheet(
                "NOPQRSTUVWXYZ",
                "##. .#. ##. .#. ##. .## ### #.# #.# #.# #.# #.# ###",
                "#.# #.# #.# #.# #.# #.. .#. #.# #.# #.# #.# #.# ..#",
                "#.# #.# ##. #.# ##. .#. .#. #.# #.# ### .#. .#. .#.",
                "#.# #.# #.. ##. #.# ..# .#. #.# #.# ### #.# .#. #..",
                "#.# .#. #.. .## #.# ##. .#. ### .#. #.# #.# .#. ###",
            ) + sheet(
                " .,:!?-+%/'",
                "... . .. . # ### ... ... #.# ..# #",
                "... . .. # # ..# ... .#. ..# ..# #",
                "... . .. . # .#. ### ### .#. .#. .",
                "... . .# # . ... ... .#. #.. #.. .",
                "... # #. . # .#. ... ... #.# #.. .",
            ),
        )

        /** 5x7: digits and . : - (for scores and timers). Four digits fit the 25-LED width; so does 12:34 on rows 9 to 15. */
        public val Big: MatrixFont = MatrixFont(
            height = 7,
            glyphs = sheet(
                "0123456789",
                ".###. ..#.. .###. ##### ...#. ##### ..##. ##### .###. .###.",
                "#...# .##.. #...# ...#. ..##. #.... .#... ....# #...# #...#",
                "#...# ..#.. ....# ..#.. .#.#. ####. #.... ...#. #...# #...#",
                "#...# ..#.. ...#. ...#. #..#. ....# ####. ..#.. .###. .####",
                "#...# ..#.. ..#.. ....# ##### ....# #...# .#... #...# ....#",
                "#...# ..#.. .#... #...# ...#. #...# #...# .#... #...# ...#.",
                ".###. .###. ##### .###. ...#. .###. .###. .#... .###. .##..",
            ) + sheet(
                " .:-",
                "..... . . ...",
                "..... . . ...",
                "..... . # ...",
                "..... . . ###",
                "..... . # ...",
                "..... . . ...",
                "..... # . ...",
            ),
        )
    }
}

/**
 * Reads glyphs drawn side by side: every line of [rows] holds one row of each glyph of [chars], in order, separated by
 * a space. '#' is a lit LED, '.' an unlit one.
 */
private fun sheet(chars: String, vararg rows: String): Map<Char, List<String>> {
    val lines = rows.map { it.split(' ') }
    require(lines.all { it.size == chars.length }) { "Font sheet \"$chars\": every line needs ${chars.length} glyphs" }
    return chars.withIndex().associate { (i, char) -> char to lines.map { it[i].replace('.', ' ') } }
}
