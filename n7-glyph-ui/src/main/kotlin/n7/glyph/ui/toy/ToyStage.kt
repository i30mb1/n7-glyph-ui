package n7.glyph.ui.toy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import n7.glyph.ui.components.GlyphMatrix
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.MatrixCanvas
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Spacing
import n7.glyph.ui.theme.N7Type

// About 30 fps whatever the display rate: a frame is due once this much time has passed (a little under 1/30 s,
// so 60, 90 and 120 Hz displays all land on every 33 ms).
private const val FRAME_DUE_MS = 29L
private const val MAX_DT_MS = 250L

/**
 * Turns vsync times into frame steps: a frame is due once [FRAME_DUE_MS] have passed, and its dtMs is the whole
 * milliseconds since the last one, at most [MAX_DT_MS]. The sub-millisecond rest is carried to the next frame, so the
 * steps add up to the real time (a 60 Hz display gives 33, 33, 34 ms, not 33 ms for every 33.3 ms frame).
 */
internal class FramePacer(startNs: Long) {
    private var lastNs = startNs

    /** The dtMs of a frame at vsync time [nowNs], or -1 when no frame is due yet. */
    fun next(nowNs: Long): Long {
        val elapsedMs = (nowNs - lastNs) / 1_000_000
        if (elapsedMs < FRAME_DUE_MS) return -1
        lastNs += elapsedMs * 1_000_000
        return elapsedMs.coerceAtMost(MAX_DT_MS)
    }
}

/**
 * The toy running on the app screen: the round matrix, driven by the phone's own sensors, with an on-screen stand-in for the
 * Glyph Button. Runs only while the screen is resumed.
 *
 * Until the first frame (and in previews) it shows [ToyEngine.still]. While the screen is not resumed the last frame
 * stays up, the engine gets no frames and the sensors are off. A new [engine] instance restarts everything for it; a
 * hold in progress on the old one still gets its [ToyEngine.onRelease]. All engine calls happen on the main thread.
 *
 * @param holdLabel label of the [GlyphHoldButton], for example "HOLD TO JUMP".
 * @param hint one line under the button, for example how to play.
 * @param contentDescription what the matrix shows, for TalkBack.
 * @param haptics play the engine's [Haptic]s (needs the VIBRATE permission in the app). They are drained either way.
 *   One vibration per frame: of a frame's batch the strongest is played, see [ToyHaptics.play].
 */
@Composable
public fun ToyStage(
    engine: ToyEngine,
    holdLabel: String,
    modifier: Modifier = Modifier,
    matrixSize: Dp = 224.dp,
    hint: String? = null,
    contentDescription: String? = null,
    haptics: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playHaptics by rememberUpdatedState(haptics)
    // Only the matrix reads this state, so a new frame recomposes the matrix and nothing else.
    val cells: MutableState<List<Cell>> = remember(engine) { mutableStateOf(MatrixCanvas.cells(engine.still())) }

    // Keyed on what the loop really depends on: other recompositions (labels, haptics, size) never restart it.
    LaunchedEffect(engine, lifecycleOwner, context) {
        val player = ToyHaptics(context)
        val motion = ToyMotionSource(context, Facing.SCREEN, engine.sensors, engine.sensorHz, engine::onMotion)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            motion.start()
            try {
                // Restarts after every pause, so the time spent paused never reaches the engine.
                val pacer = FramePacer(withFrameNanos { it })
                while (true) {
                    val dtMs = pacer.next(withFrameNanos { it })
                    if (dtMs < 0) continue
                    // Converted at once: an engine may reuse its frame array.
                    cells.value = MatrixCanvas.cells(engine.frame(dtMs))
                    val asked = engine.drainHaptics()
                    if (playHaptics) player.play(asked)
                }
            } finally {
                motion.stop()
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StageMatrix(cells, matrixSize, contentDescription)
        Spacer(Modifier.height(N7Spacing.Space6))
        // A new engine gets a new button; disposing the old one releases a hold the old engine is in.
        key(engine) {
            GlyphHoldButton(label = holdLabel, onLongPress = engine::onLongPress, onRelease = engine::onRelease)
        }
        if (hint != null) {
            Spacer(Modifier.height(N7Spacing.Space3))
            Text(hint, style = N7Type.BodyS, color = N7Colors.InkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StageMatrix(cells: State<List<Cell>>, size: Dp, contentDescription: String?) {
    GlyphMatrix(cells = cells.value, modifier = Modifier.size(size), contentDescription = contentDescription)
}
