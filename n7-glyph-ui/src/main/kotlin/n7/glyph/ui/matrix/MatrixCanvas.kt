package n7.glyph.ui.matrix

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A 25x25 picture for the Glyph Matrix, as seen by someone looking at the back of the phone: x grows to the right,
 * y grows down, LED (12, 12) is the centre. Brightness per LED is 0..[MAX].
 *
 * Everything outside the round outline ([GlyphMatrixSpec.isVisible]) is silently ignored on write and reads as 0, so
 * shapes may run off the edge freely.
 *
 * Outline shapes ([line], [rect], [circle], [arc], [rim]) keep the brighter of old and new, so crossing lines never dim
 * each other. Filled shapes ([fill], [fillRect], [disc]), [sprite] and [text] overwrite, so they also erase: draw them
 * with level 0 to cut a hole.
 *
 * Not thread-safe: draw and read on one thread.
 */
public class MatrixCanvas {

    /** The picture: [SIZE] x [SIZE] values row by row, index = y * [SIZE] + x. Writing it directly skips clipping. */
    public val pixels: IntArray = IntArray(SIZE * SIZE)

    /** Turns every LED off. Returns this canvas for chaining. */
    public fun clear(): MatrixCanvas {
        pixels.fill(0)
        return this
    }

    /** Brightness of LED ([x], [y]); 0 outside the round outline. */
    public operator fun get(x: Int, y: Int): Int = if (GlyphMatrixSpec.isVisible(x, y)) pixels[y * SIZE + x] else 0

    /** Sets LED ([x], [y]) to [level], clamped to 0..[MAX]. */
    public operator fun set(x: Int, y: Int, level: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) pixels[y * SIZE + x] = level.coerceIn(0, MAX)
    }

    /** Adds [level] (may be negative) to LED ([x], [y]), clamped to 0..[MAX]. */
    public fun add(x: Int, y: Int, level: Int) {
        if (!GlyphMatrixSpec.isVisible(x, y)) return
        val i = y * SIZE + x
        // Long: two large levels must not overflow before the clamp.
        pixels[i] = (pixels[i].toLong() + level).coerceIn(0L, MAX.toLong()).toInt()
    }

    /** Raises LED ([x], [y]) to [level] if it is dimmer; never darkens it. */
    public fun lighten(x: Int, y: Int, level: Int) {
        if (!GlyphMatrixSpec.isVisible(x, y)) return
        val i = y * SIZE + x
        pixels[i] = max(pixels[i], level.coerceIn(0, MAX))
    }

    /** Sets every visible LED to [level]. */
    public fun fill(level: Int) {
        for (y in 0 until SIZE) for (x in GlyphMatrixSpec.rowRange(y)) set(x, y, level)
    }

    /**
     * A straight line from ([x0], [y0]) to ([x1], [y1]), both ends included, in any direction (Bresenham). Lightens.
     * Any Int ends work: a line reaching far off the canvas is first cut to the part near it, so it costs no more than
     * a short one (its LEDs then follow the true line to within one LED).
     */
    public fun line(x0: Int, y0: Int, x1: Int, y1: Int, level: Int) {
        if (max(max(abs(x0.toLong()), abs(y0.toLong())), max(abs(x1.toLong()), abs(y1.toLong()))) <= NEAR) {
            walk(x0, y0, x1, y1, level)
            return
        }
        // Liang-Barsky against the canvas plus a 1-LED margin, in Double: exact enough for any Int coordinates.
        val dx = x1.toDouble() - x0
        val dy = y1.toDouble() - y0
        var t0 = 0.0
        var t1 = 1.0
        fun keep(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val t = q / p
            if (p < 0.0) {
                if (t > t1) return false
                if (t > t0) t0 = t
            } else {
                if (t < t0) return false
                if (t < t1) t1 = t
            }
            return true
        }
        val lo = -1.0
        val hi = SIZE.toDouble()
        if (!(keep(-dx, x0 - lo) && keep(dx, hi - x0) && keep(-dy, y0 - lo) && keep(dy, hi - y0))) return
        fun at(start: Int, d: Double, t: Double) = (start + d * t).roundToInt().coerceIn(-1, SIZE)
        walk(at(x0, dx, t0), at(y0, dy, t0), at(x0, dx, t1), at(y0, dy, t1), level)
    }

    // Plain Bresenham; the caller keeps every coordinate within NEAR, so nothing overflows and the walk is short.
    private fun walk(x0: Int, y0: Int, x1: Int, y1: Int, level: Int) {
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var x = x0
        var y = y0
        while (true) {
            lighten(x, y, level)
            if (x == x1 && y == y1) return
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Outline of the [w] x [h] rectangle whose top-left LED is ([x], [y]). Nothing when [w] or [h] is below 1. Lightens. */
    public fun rect(x: Int, y: Int, w: Int, h: Int, level: Int) {
        if (w < 1 || h < 1) return
        // Far edges in Long, pulled in to just off the canvas (where they draw nothing, as the real edge would).
        val r = farEdge(x, w)
        val b = farEdge(y, h)
        for (i in max(x, 0)..min(r, SIZE - 1)) { lighten(i, y, level); lighten(i, b, level) }
        for (j in max(y, 0)..min(b, SIZE - 1)) { lighten(x, j, level); lighten(r, j, level) }
    }

    /** The filled [w] x [h] rectangle whose top-left LED is ([x], [y]). Overwrites. */
    public fun fillRect(x: Int, y: Int, w: Int, h: Int, level: Int) {
        if (w < 1 || h < 1) return
        val r = farEdge(x, w)
        val b = farEdge(y, h)
        for (j in max(y, 0)..min(b, SIZE - 1)) for (i in max(x, 0)..min(r, SIZE - 1)) set(i, j, level)
    }

    // The last LED of a run of [length] (>= 1) from [start], without overflow, clamped to -1..SIZE.
    private fun farEdge(start: Int, length: Int): Int = (start.toLong() + length - 1).coerceIn(-1L, SIZE.toLong()).toInt()

    /**
     * A 1-LED outline of the circle with centre ([cx], [cy]) and radius [r], in LED units: every LED whose centre lies
     * within half an LED of the circle. Use x.5 centres for circles between LEDs. Lightens.
     */
    public fun circle(cx: Float, cy: Float, r: Float, level: Int) {
        if (r < 0f) return
        forBand(cx, cy, r) { x, y -> lighten(x, y, level) }
    }

    /** The filled circle: every LED whose centre is closer than r + 0.5, so it covers [circle] with the same arguments. Overwrites. */
    public fun disc(cx: Float, cy: Float, r: Float, level: Int) {
        if (r < 0f) return
        val outer = r + 0.5f
        forBox(cx, cy, outer) { x, y ->
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy < outer * outer) set(x, y, level)
        }
    }

    /**
     * The part of [circle] from [startDeg] going [sweepDeg] degrees: 0 deg is 12 o'clock, positive sweeps run clockwise,
     * negative ones counter-clockwise. A sweep of 360 or more is the whole circle. An LED belongs to the arc when the angle
     * of its centre does. Lightens.
     */
    public fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, level: Int) {
        if (r < 0f || sweepDeg == 0f) return
        if (abs(sweepDeg) >= 360f) return circle(cx, cy, r, level)
        val from = if (sweepDeg > 0f) startDeg else startDeg + sweepDeg
        val sweep = abs(sweepDeg)
        forBand(cx, cy, r) { x, y ->
            val delta = (angleOf(x - cx, y - cy) - from).mod(360f)
            // A tiny tolerance keeps LEDs that sit exactly on the end angle.
            if (delta <= sweep + 1e-3f) lighten(x, y, level)
        }
    }

    /**
     * Lights the first [fraction] (0..1, clamped) of [RIM], clockwise from `RIM[startIndex]` (any index, wrapped), as a
     * progress ring: round(fraction * 68) LEDs. A NaN fraction (say 0 / 0) lights nothing. Lightens.
     */
    public fun rim(fraction: Float, level: Int, startIndex: Int = 0) {
        if (fraction.isNaN()) return
        val n = RIM.size
        val lit = (fraction.coerceIn(0f, 1f) * n).roundToInt()
        // Wrapped first, so startIndex + k cannot overflow.
        val start = startIndex.mod(n)
        for (k in 0 until lit) {
            val (x, y) = RIM[(start + k) % n]
            lighten(x, y, level)
        }
    }

    /**
     * Draws a picture given as text rows, top-left at ([x], [y]): '#' is [level], '+' 60 % of it, '.' 25 %, any other
     * character is transparent. Overwrites.
     */
    public fun sprite(rows: List<String>, x: Int, y: Int, level: Int = MAX) {
        val full = level.coerceIn(0, MAX)
        val mid = full * 3 / 5
        val low = full / 4
        for ((j, row) in rows.withIndex()) {
            for ((i, c) in row.withIndex()) {
                when (c) {
                    '#' -> set(x + i, y + j, full)
                    '+' -> set(x + i, y + j, mid)
                    '.' -> set(x + i, y + j, low)
                }
            }
        }
    }

    /**
     * Writes [text] with its top-left at ([x], [y]), glyphs [MatrixFont.GAP] LED apart; lit dots overwrite, the rest
     * stays. Characters the font lacks leave a space-wide gap. Returns the column right after the last glyph,
     * `x + font.width(text)`.
     */
    public fun text(text: String, x: Int, y: Int, level: Int = MAX, font: MatrixFont = MatrixFont.Small): Int {
        var cx = x
        for ((i, char) in text.withIndex()) {
            if (i > 0) cx += MatrixFont.GAP
            val glyph = font.glyph(char)
            if (glyph != null) {
                for ((j, row) in glyph.withIndex()) {
                    for ((k, c) in row.withIndex()) if (c == '#') set(cx + k, y + j, level)
                }
            }
            cx += font.advance(char)
        }
        return cx
    }

    /** Writes [text] centred on column 12, top row at [y]. An even width has its extra column left of the centre. */
    public fun textCentered(text: String, y: Int, level: Int = MAX, font: MatrixFont = MatrixFont.Small) {
        text(text, (SIZE - font.width(text)) / 2, y, level, font)
    }

    /** A copy of the picture, ready for `GlyphMatrixManager.setMatrixFrame`. */
    public fun frame(): IntArray = pixels.copyOf()

    /** The lit LEDs for the Compose `GlyphMatrix`; see [Companion.cells]. */
    public fun cells(): List<Cell> = cells(pixels)

    private inline fun forBox(cx: Float, cy: Float, reach: Float, action: (Int, Int) -> Unit) {
        val y0 = max(0, floor(cy - reach).toInt())
        val y1 = min(SIZE - 1, ceil(cy + reach).toInt())
        val x0 = max(0, floor(cx - reach).toInt())
        val x1 = min(SIZE - 1, ceil(cx + reach).toInt())
        for (y in y0..y1) for (x in x0..x1) action(x, y)
    }

    // LEDs whose centre is within half an LED of the circle: a clean 1-LED ring at any radius.
    private inline fun forBand(cx: Float, cy: Float, r: Float, action: (Int, Int) -> Unit) {
        val inner = r - 0.5f
        val outer = r + 0.5f
        forBox(cx, cy, outer) { x, y ->
            val dx = x - cx
            val dy = y - cy
            val d2 = dx * dx + dy * dy
            if (d2 < outer * outer && (inner <= 0f || d2 >= inner * inner)) action(x, y)
        }
    }

    /** Size, full brightness, the rim loop and frame-to-cells conversion. */
    public companion object {
        /** LEDs per side. */
        public const val SIZE: Int = GlyphMatrixSpec.SIZE

        /** Full brightness of one LED. */
        public const val MAX: Int = 4095

        /**
         * The 68 LEDs on the edge of the round matrix, clockwise, starting at 12 o'clock (12, 0). Every LED is the
         * 8-neighbour of the one before it, and the last is the neighbour of the first.
         */
        public val RIM: List<Pair<Int, Int>> = buildRim()

        /**
         * Frame brightness to `GlyphMatrix` cells, for showing a frame on the screen. Perceptual: level = (v / MAX)^0.45,
         * at least 0.12 for any lit LED, so faint LEDs that glow on the device stay visible on the screen. Values outside
         * 0..MAX are clamped; LEDs outside the outline and dark LEDs are left out.
         */
        public fun cells(frame: IntArray): List<Cell> {
            val out = ArrayList<Cell>()
            for (y in 0 until SIZE) {
                for (x in GlyphMatrixSpec.rowRange(y)) {
                    val v = frame.getOrElse(y * SIZE + x) { 0 }
                    if (v <= 0) continue
                    val level = (min(v, MAX).toFloat() / MAX).pow(0.45f)
                    out += Cell(x, y, max(level, MIN_CELL_LEVEL))
                }
            }
            return out
        }

        private const val MIN_CELL_LEVEL = 0.12f

        // Lines with every coordinate within this are walked as given (exact Bresenham); longer ones are clipped first.
        private const val NEAR = 4096L

        // Degrees clockwise from 12 o'clock with y pointing down, 0 until 360.
        private fun angleOf(dx: Float, dy: Float): Float =
            Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat().mod(360f)

        // An edge LED is a visible one with an invisible 4-neighbour. Sorting the 68 of them by angle gives the loop;
        // the tests check that every step goes to an 8-neighbour.
        private fun buildRim(): List<Pair<Int, Int>> {
            val v = GlyphMatrixSpec::isVisible
            val edge = ArrayList<Pair<Int, Int>>()
            for (y in 0 until SIZE) for (x in GlyphMatrixSpec.rowRange(y)) {
                if (!(v(x - 1, y) && v(x + 1, y) && v(x, y - 1) && v(x, y + 1))) edge += x to y
            }
            val c = (SIZE - 1) / 2f
            return edge.sortedBy { (x, y) -> angleOf(x - c, y - c) }
        }
    }
}
