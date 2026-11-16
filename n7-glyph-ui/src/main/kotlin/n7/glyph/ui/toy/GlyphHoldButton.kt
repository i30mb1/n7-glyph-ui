package n7.glyph.ui.toy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type

private enum class HoldPhase { Idle, Pressed, Held }

/**
 * On-screen stand-in for the Glyph Button. Works like the real one: a press shorter than [longPressMs] does nothing,
 * holding longer fires [onLongPress], letting go after that fires [onRelease].
 *
 * [onRelease] fires exactly once after each [onLongPress] and never without one: also when the touch is cancelled,
 * when [longPressMs] changes mid-hold and when the button leaves the composition while held. If the callbacks change
 * mid-hold, the release still goes to the [onRelease] given together with the [onLongPress] that fired. Before the long press,
 * sliding off the pill or a parent starting to scroll cancels the press; after it, the hold lasts until the finger
 * is lifted wherever it is, and the pill keeps the touch so a scrolling parent cannot steal it.
 *
 * For TalkBack it is a button whose click fires [onLongPress] immediately followed by [onRelease].
 */
@Composable
public fun GlyphHoldButton(
    label: String,
    onLongPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    longPressMs: Long = 350L,
) {
    // The gesture loop must survive recompositions; each hold presses the latest onLongPress and releases its partner.
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var phase by remember { mutableStateOf(HoldPhase.Idle) }

    val held = phase == HoldPhase.Held
    val fill = when (phase) {
        HoldPhase.Idle -> Color.Transparent
        HoldPhase.Pressed -> N7Colors.SurfaceRaised
        HoldPhase.Held -> N7Colors.Primary
    }
    Box(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (held) N7Colors.Primary else N7Colors.ControlBorder, CircleShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    // A real hold in progress already owns the pair.
                    if (phase == HoldPhase.Idle) {
                        currentOnLongPress()
                        currentOnRelease()
                    }
                    true
                }
            }
            .pointerInput(longPressMs) {
                awaitEachGesture {
                    awaitFirstDown()
                    phase = HoldPhase.Pressed
                    // The onRelease paired with the onLongPress that fired: new callbacks mid-hold must not get a
                    // release without a press, nor leave the old ones pressed.
                    var release: (() -> Unit)? = null
                    try {
                        // Unit when the press ended early (lifted, slid off, taken by a parent), null when it outlasted the timeout.
                        val endedEarly = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() ; Unit }
                        if (endedEarly == null) {
                            val pairedRelease = currentOnRelease
                            currentOnLongPress()
                            release = pairedRelease
                            phase = HoldPhase.Held
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    } finally {
                        // Also runs when the gesture coroutine is cancelled: disposal, a new longPressMs.
                        phase = HoldPhase.Idle
                        release?.invoke()
                    }
                }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = N7Type.BodyS.copy(fontWeight = FontWeight.Medium),
            color = if (held) N7Colors.OnPrimary else N7Colors.Ink,
            textAlign = TextAlign.Center,
        )
    }
}
