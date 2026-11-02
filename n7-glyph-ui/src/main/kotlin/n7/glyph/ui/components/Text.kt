package n7.glyph.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import n7.glyph.ui.R
import n7.glyph.ui.theme.GeistFontFamily
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type

private val Cyrillic = Regex("[\\u0400-\\u04FF]")

/**
 * Stacked short headline with a red full stop after each line.
 * Lines with Cyrillic are set in Geist Bold: Doto has no Cyrillic glyphs.
 */
@Composable
public fun DotHeadline(
    lines: List<String>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 56.sp,
    stops: Boolean = true,
) {
    val cyrillic = lines.any { Cyrillic.containsMatchIn(it) }
    val style = if (cyrillic) {
        N7Type.Headline.copy(fontFamily = GeistFontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = 1.em, letterSpacing = (-0.02).em)
    } else {
        N7Type.DisplayL.copy(fontSize = fontSize, lineHeight = 0.95.em)
    }
    val dot = with(LocalDensity.current) { (fontSize * 0.14f).toDp() }
    val gap = with(LocalDensity.current) { (fontSize * 0.1f).toDp() }
    Column(modifier) {
        lines.forEach { line ->
            Row {
                Text(line, style = style, color = N7Colors.Ink, modifier = Modifier.alignByBaseline(), maxLines = 1)
                if (stops) {
                    Spacer(Modifier.width(gap))
                    Box(
                        Modifier
                            .alignBy { it.measuredHeight }
                            .size(dot)
                            .background(N7Colors.Signal, CircleShape),
                    )
                }
            }
        }
    }
}

/** The family signature: red dot + MADE FOR GLYPH MATRIX. Put it on every main screen. */
@Composable
public fun SignatureLabel(modifier: Modifier = Modifier, text: String = stringResource(R.string.n7_made_for_glyph_matrix)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(N7Colors.Signal, CircleShape))
        Text(text.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle)
    }
}

/** A mono caption over a Doto number, zero-padded. */
@Composable
public fun ScoreDisplay(value: Int, modifier: Modifier = Modifier, label: String = "Best", digits: Int = 3) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle)
        Text(value.toString().padStart(digits, '0'), style = N7Type.Score, color = N7Colors.Ink)
    }
}

/** Uppercase mono section label. */
@Composable
public fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle, modifier = modifier)
}
