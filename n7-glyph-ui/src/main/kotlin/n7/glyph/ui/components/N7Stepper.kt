package n7.glyph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Radius
import n7.glyph.ui.theme.N7Type

/** Grouped content on [N7Colors.SurfaceRaised] with the card radius. Separate rows with [N7Divider]. */
@Composable
public fun N7Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(N7Radius.Card))
            .background(N7Colors.SurfaceRaised)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        content = content,
    )
}

/** Hairline between rows of an [N7Card]. */
@Composable
public fun N7Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = N7Colors.Hairline)
}

/** A setting row: label on the left, − value + on the right, the value in Doto. */
@Composable
public fun N7Stepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = N7Type.BodyS, color = N7Colors.Ink, modifier = Modifier.weight(1f))
        StepButton("−", "Decrease $label", canDecrease, onDecrease)
        Text(
            value,
            style = N7Type.DisplayS,
            color = N7Colors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        StepButton("+", "Increase $label", canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) N7Colors.ControlBorder else N7Colors.Hairline),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = N7Colors.Ink, disabledContentColor = N7Colors.InkSubtle),
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Text(symbol, style = N7Type.Title)
    }
}
