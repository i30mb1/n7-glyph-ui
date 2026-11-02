package n7.glyph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type

public enum class N7ButtonVariant { Primary, Secondary }

/** Pill button. One [N7ButtonVariant.Primary] per screen; the rest [N7ButtonVariant.Secondary]. Never red. */
@Composable
public fun N7Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: N7ButtonVariant = N7ButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    val sized = modifier.defaultMinSize(minHeight = 48.dp)
    val label = @Composable { Text(text, style = N7Type.BodyS.copy(fontWeight = FontWeight.Medium)) }
    when (variant) {
        N7ButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = N7Colors.Primary,
                contentColor = N7Colors.OnPrimary,
                disabledContainerColor = N7Colors.Primary.copy(alpha = 0.4f),
                disabledContentColor = N7Colors.OnPrimary.copy(alpha = 0.6f),
            ),
        ) { label() }
        N7ButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            border = BorderStroke(1.dp, N7Colors.ControlBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = N7Colors.Ink),
        ) { label() }
    }
}
