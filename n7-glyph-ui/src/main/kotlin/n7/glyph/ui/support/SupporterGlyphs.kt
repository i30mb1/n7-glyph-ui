package n7.glyph.ui.support

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The supporter's pictures on the matrix, the same in every n7 app: a steaming cup of coffee (the thank-you after the
 * purchase) and sparkles running round the rim (each time a toy starts). Pure: frames in, frames out.
 */
public object SupporterGlyphs {
    private const val SIZE = GlyphMatrixSpec.SIZE
    public const val MAX: Int = 4095
    private const val CUP_LEVEL = 1700

    /** How long the thank-you plays, and the sparkles at the start of a toy. */
    public const val COFFEE_MS: Long = 3600L
    public const val SPARKLE_MS: Long = 1600L

    /** The cup, left edge at x = 5, top at y = 12: '#' the cup, 'o' the bright rim, handle and saucer. */
    private val CUP = listOf(
        "..ooooooooooo...",
        "..###########oo.",
        "..###########..o",
        "..###########..o",
        "...#########..o.",
        "...#########oo..",
        "....#######.....",
        ".....#####......",
        "................",
        ".ooooooooooooo..",
    )
    private const val CUP_X = 5
    private const val CUP_Y = 12

    /** Where the three wisps of steam rise from, and how high they reach. */
    private val STEAM_X = intArrayOf(9, 12, 15)
    private const val STEAM_BOTTOM = 10
    private const val STEAM_TOP = 2

    /**
     * One frame of the coffee, [timeMs] into it: the cup fades in, steam rises in three wavy wisps, and it all fades out
     * at the end of [COFFEE_MS].
     */
    public fun coffee(timeMs: Long): IntArray {
        val frame = IntArray(SIZE * SIZE)
        val fade = when {
            timeMs < 400 -> timeMs / 400f
            timeMs > COFFEE_MS - 500 -> ((COFFEE_MS - timeMs) / 500f).coerceAtLeast(0f)
            else -> 1f
        }
        CUP.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                val level = when (c) {
                    '#' -> CUP_LEVEL
                    'o' -> MAX
                    else -> return@forEachIndexed
                }
                put(frame, CUP_X + dx, CUP_Y + dy, (level * fade).roundToInt())
            }
        }
        val rise = timeMs / 110.0
        STEAM_X.forEachIndexed { i, x0 ->
            for (y in STEAM_TOP..STEAM_BOTTOM) {
                // Each wisp is a sine wave that scrolls upwards; it is brightest near the cup and fades as it rises.
                val phase = (y + rise + i * 2.3) / 2.2
                val x = x0 + (sin(phase) * 1.2).roundToInt()
                val height = (STEAM_BOTTOM - y).toFloat() / (STEAM_BOTTOM - STEAM_TOP)
                // Gaps run up the wisp, so it reads as puffs of steam, not a static line.
                val puff = ((y + rise.toInt() + i) % 4 != 0)
                if (puff) put(frame, x, y, (MAX * (1f - 0.7f * height) * fade).roundToInt())
            }
        }
        return frame
    }

    /** The cells of the outermost ring of the round matrix, clockwise from the top. */
    public val RIM: List<Pair<Int, Int>> = buildList {
        for (y in 0 until SIZE) {
            val r = GlyphMatrixSpec.rowRange(y)
            // A cell is on the rim when it is the end of its row or has no LED above or below it.
            for (x in r) {
                if (x == r.first || x == r.last || !GlyphMatrixSpec.isVisible(x, y - 1) || !GlyphMatrixSpec.isVisible(x, y + 1)) add(x to y)
            }
        }
    }.sortedBy { (x, y) -> (atan2((x - SIZE / 2).toDouble(), (SIZE / 2 - y).toDouble()) + 2 * PI) % (2 * PI) }

    /**
     * Sparkles chasing round the rim, [timeMs] into the [SPARKLE_MS] intro, drawn over [frame] (they only light,
     * never darken). Each sparkle twinkles up and fades; the whole ring dies away at the end.
     */
    public fun sparkle(frame: IntArray, timeMs: Long) {
        if (timeMs !in 0 until SPARKLE_MS) return
        val fade = 1f - timeMs.toFloat() / SPARKLE_MS
        val n = RIM.size
        val head = (timeMs / 18).toInt()
        for (k in 0 until 12) {
            // Two chases opposite each other, a trail of fading sparkles behind each head.
            for (side in 0..1) {
                val i = ((head - k + side * n / 2) % n + n) % n
                val (x, y) = RIM[i]
                val twinkle = 0.5f + 0.5f * sin((timeMs / 60.0 + i) * PI / 2).toFloat()
                val level = MAX * (1f - k / 12f) * fade * (0.6f + 0.4f * twinkle)
                put(frame, x, y, level.roundToInt())
            }
        }
    }

    private fun put(frame: IntArray, x: Int, y: Int, level: Int) {
        if (!GlyphMatrixSpec.isVisible(x, y)) return
        val i = y * SIZE + x
        if (level > frame[i]) frame[i] = level.coerceAtMost(MAX)
    }
}
