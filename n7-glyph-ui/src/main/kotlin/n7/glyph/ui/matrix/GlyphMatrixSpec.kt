package n7.glyph.ui.matrix

/** Physical shape of the Phone (3) Glyph Matrix: 25x25 cells inside a round outline. */
public object GlyphMatrixSpec {
    public const val SIZE: Int = 25

    // Visible x-range per row, measured on the device.
    private val BOUNDS: Array<IntRange> = arrayOf(
        9..15, 7..17, 5..19, 4..20, 3..21, 2..22, 2..22, 1..23, 1..23,
        0..24, 0..24, 0..24, 0..24, 0..24, 0..24, 0..24,
        1..23, 1..23, 2..22, 2..22, 3..21, 4..20, 5..19, 7..17, 9..15,
    )

    /** Visible x-range of row [y]; empty outside the matrix. */
    public fun rowRange(y: Int): IntRange = BOUNDS.getOrNull(y) ?: IntRange.EMPTY

    /** Whether cell ([x], [y]) is a real LED. */
    public fun isVisible(x: Int, y: Int): Boolean = x in rowRange(y)
}

/** One LED: [level] 0..1, 1 = full white. */
public data class Cell(val x: Int, val y: Int, val level: Float = 1f)

/** Helpers and the family's shared pictures. */
public object Frames {

    /** Straight line of cells from (x0, y0) to (x1, y1), horizontal or vertical, ends included. */
    public fun line(x0: Int, y0: Int, x1: Int, y1: Int): List<Cell> {
        val out = mutableListOf(Cell(x0, y0))
        var x = x0
        var y = y0
        while (x != x1 || y != y1) {
            if (x != x1) x += Integer.signum(x1 - x) else y += Integer.signum(y1 - y)
            out += Cell(x, y)
        }
        return out
    }

    /** A snake along [path] (tail first): body ramps up in brightness, head is full. */
    public fun snake(path: List<Cell>): List<Cell> = path.mapIndexed { i, c ->
        c.copy(level = if (i == path.lastIndex) 1f else 0.45f + 0.35f * i / path.size)
    }

    /** Glyph Snake icon: an L-shaped snake; the food is the red [SnakeIconSignal]. */
    public val SnakeIcon: List<Cell> = snake(line(9, 8, 15, 8) + line(15, 9, 15, 13))
    public val SnakeIconSignal: List<Cell> = listOf(Cell(18, 16))

    /** Flappy Box icon: two pipes; the bird is the red [FlappyIconSignal]. */
    public val FlappyIcon: List<Cell> = buildList {
        for (y in 0 until GlyphMatrixSpec.SIZE) {
            if (y < 7 || y > 16) { add(Cell(4, y, 0.5f)); add(Cell(5, y, 0.5f)) }
            if (y < 4 || y > 13) { add(Cell(18, y, 0.5f)); add(Cell(19, y, 0.5f)) }
        }
    }
    public val FlappyIconSignal: List<Cell> = listOf(Cell(11, 11), Cell(12, 11), Cell(11, 12), Cell(12, 12))
}
