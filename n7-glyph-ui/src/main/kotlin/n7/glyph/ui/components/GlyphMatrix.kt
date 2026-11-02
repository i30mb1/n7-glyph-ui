package n7.glyph.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.theme.N7Colors

/**
 * The Phone (3) Glyph Matrix: 25x25 LEDs in the device's round outline, unlit cells visible.
 *
 * @param cells lit LEDs; level 1 = full white.
 * @param signal cells drawn in [N7Colors.Signal]. Icons and brand art only: the real LEDs are white.
 * @param lens draw the dark glass disc and rim behind the LEDs.
 * @param glow soft halo around lit LEDs.
 */
@Composable
public fun GlyphMatrix(
    cells: List<Cell>,
    modifier: Modifier = Modifier,
    signal: List<Cell> = emptyList(),
    lens: Boolean = true,
    glow: Boolean = true,
    contentDescription: String? = null,
) {
    val lit = remember(cells) { cells.associate { (it.x to it.y) to it.level } }
    val red = remember(signal) { signal.mapTo(HashSet()) { it.x to it.y } }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        val side = size.minDimension
        val span = if (lens) 28.2f else 25f
        val unit = side / span
        val pad = if (lens) 1.6f * unit else 0f
        val origin = Offset((size.width - side) / 2f + pad, (size.height - side) / 2f + pad)
        fun center(x: Int, y: Int) = origin + Offset((x + 0.5f) * unit, (y + 0.5f) * unit)

        if (lens) {
            val c = origin + Offset(12.5f * unit, 12.5f * unit)
            drawCircle(N7Colors.Lens, radius = 13.85f * unit, center = c)
            drawCircle(N7Colors.LensRim, radius = 13.85f * unit, center = c, style = Stroke(width = 0.22f * unit))
        }
        val r = 0.38f * unit
        for (y in 0 until GlyphMatrixSpec.SIZE) {
            for (x in GlyphMatrixSpec.rowRange(y)) {
                val key = x to y
                val level = lit[key]
                val p = center(x, y)
                when {
                    key in red -> drawCircle(N7Colors.Signal, r, p)
                    level != null -> {
                        if (glow) drawCircle(N7Colors.LedOn.copy(alpha = 0.22f * level), r * 2.1f, p)
                        val base = if (level >= 0.9f) N7Colors.LedOn else N7Colors.LedMid
                        drawCircle(base.copy(alpha = 0.55f + 0.45f * level), r, p)
                    }
                    else -> drawCircle(N7Colors.LedOff, r, p)
                }
            }
        }
    }
}
