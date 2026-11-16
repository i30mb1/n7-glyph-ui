package n7.glyph.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import n7.glyph.ui.theme.N7Colors

/**
 * The row of dots under anything that is swiped sideways one page at a time: one LED per page, lit for the page
 * on screen. Put it under a `HorizontalPager` and pass `pagerState.currentPage`.
 */
@Composable
public fun N7PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(Modifier.size(6.dp).background(if (i == current) N7Colors.LedOn else N7Colors.LensRim, CircleShape))
        }
    }
}
