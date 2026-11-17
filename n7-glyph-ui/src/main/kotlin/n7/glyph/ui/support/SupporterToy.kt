package n7.glyph.ui.support

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.toy.Haptic
import n7.glyph.ui.toy.Motion
import n7.glyph.ui.toy.ToyEngine
import n7.glyph.ui.toy.ToySensors

/** What [SupporterToy] needs to know about the purchase. [Supporter] keeps it in the app's preferences. */
public interface SupporterState {
    /** The player bought Supporter. */
    public val owned: Boolean

    /** The coffee was shown on the matrix once after the purchase. */
    public var thanked: Boolean
}

/**
 * Wraps a toy for supporters, the same in every n7 app. The first time a toy starts after the purchase the matrix
 * shows the cup of coffee ([SupporterGlyphs.coffee]) before the toy; from then on every start opens with
 * sparkles round the rim ([SupporterGlyphs.sparkle]) over the toy. For everybody else it is the toy, untouched.
 *
 * The toy does not run while the coffee plays (no frames, no button), so it starts fresh after the thank-you.
 */
public class SupporterToy(private val toy: ToyEngine, private val state: SupporterState) : ToyEngine {

    private var timeMs = 0L
    private var coffee: Boolean? = null
    private var sparkleFrom = 0L
    private var holding = false

    override val sensors: ToySensors get() = toy.sensors
    override val sensorHz: Int get() = toy.sensorHz

    override fun onLongPress() {
        if (coffee == true) return
        holding = true
        toy.onLongPress()
    }

    override fun onRelease() {
        if (!holding) return
        holding = false
        toy.onRelease()
    }

    override fun onMotion(motion: Motion) {
        if (coffee != true) toy.onMotion(motion)
    }

    override fun frame(dtMs: Long): IntArray {
        if (coffee == null) coffee = state.owned && !state.thanked
        timeMs += dtMs
        if (coffee == true) {
            if (timeMs < SupporterGlyphs.COFFEE_MS) return SupporterGlyphs.coffee(timeMs)
            coffee = false
            state.thanked = true
            sparkleFrom = timeMs
        }
        val frame = toy.frame(dtMs)
        if (state.owned) {
            val t = timeMs - sparkleFrom
            if (t < SupporterGlyphs.SPARKLE_MS) {
                val out = frame.copyOf()
                SupporterGlyphs.sparkle(out, t)
                return out
            }
        }
        return frame
    }

    override fun still(): IntArray = toy.still()

    override fun drainHaptics(): List<Haptic> = toy.drainHaptics()
}

/**
 * The same opening as [SupporterToy] for a toy that is not a [ToyEngine] (it draws its own frames): the host plays
 * [frame] for [durationMs] before its toy starts. The coffee once after the purchase, then the sparkles; nothing at all
 * ([durationMs] 0) for a player who is not a supporter.
 */
public class SupporterIntro(private val state: SupporterState) {
    private val coffee = state.owned && !state.thanked

    /** How long the opening lasts, ms. */
    public val durationMs: Long = when {
        !state.owned -> 0L
        coffee -> SupporterGlyphs.COFFEE_MS + SupporterGlyphs.SPARKLE_MS
        else -> SupporterGlyphs.SPARKLE_MS
    }

    /** The frame [timeMs] into the opening, the sparkles drawn over [under] (the toy's first picture, or nothing). */
    public fun frame(timeMs: Long, under: IntArray = IntArray(GlyphMatrixSpec.SIZE * GlyphMatrixSpec.SIZE)): IntArray {
        if (coffee && timeMs < SupporterGlyphs.COFFEE_MS) return SupporterGlyphs.coffee(timeMs)
        if (coffee && !state.thanked) state.thanked = true
        val out = under.copyOf()
        SupporterGlyphs.sparkle(out, if (coffee) timeMs - SupporterGlyphs.COFFEE_MS else timeMs)
        return out
    }
}
