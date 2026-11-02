package n7.glyph.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Radius
import n7.glyph.ui.theme.N7Type

/** A Glyph Toy row: matrix thumbnail, title, two-line summary. */
@Composable
public fun ToyCard(
    title: String,
    cells: List<Cell>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    signal: List<Cell> = emptyList(),
    matrixSize: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(N7Radius.Card)
    Row(
        modifier
            .clip(shape)
            .background(N7Colors.SurfaceRaised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlyphMatrix(cells = cells, signal = signal, glow = false, modifier = Modifier.size(matrixSize))
        Column(Modifier.weight(1f)) {
            Text(title, style = N7Type.Title, color = N7Colors.Ink)
            if (summary != null) {
                Text(summary, style = N7Type.BodyS, color = N7Colors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
