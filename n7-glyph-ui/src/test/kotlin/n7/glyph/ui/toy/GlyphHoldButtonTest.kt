package n7.glyph.ui.toy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlyphHoldButtonTest {

    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()
    private var generation by mutableStateOf(1)
    private var shown by mutableStateOf(true)
    private var longPressMs by mutableLongStateOf(350L)

    @Before
    fun setUp() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (shown) {
                val g = generation
                GlyphHoldButton(
                    label = "HOLD $g",
                    onLongPress = { events += "press$g" },
                    onRelease = { events += "release$g" },
                    longPressMs = longPressMs,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun button() = rule.onNodeWithText("HOLD $generation")

    // Applies pending state changes first, so the frames in [ms] recompose with them.
    private fun advance(ms: Long) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun shortPressDoesNothing() {
        button().performTouchInput { down(center) }
        advance(200)
        button().performTouchInput { up() }
        advance(1000)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun holdFiresLongPressThenReleaseOnLift() {
        button().performTouchInput { down(center) }
        advance(300)
        assertEquals(emptyList<String>(), events)
        advance(100)
        assertEquals(listOf("press1"), events)
        advance(2000)
        assertEquals(listOf("press1"), events)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
        // A second hold works the same.
        button().performTouchInput { down(center) }
        advance(500)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press1", "release1", "press1", "release1"), events)
    }

    @Test
    fun cancelledTouchStillReleases() {
        button().performTouchInput { down(center) }
        advance(400)
        button().performTouchInput { cancel() }
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun cancelBeforeTheLongPressDoesNothing() {
        button().performTouchInput { down(center) }
        advance(100)
        button().performTouchInput { cancel() }
        advance(1000)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun slidingOffBeforeTheLongPressCancels() {
        button().performTouchInput { down(center); moveTo(Offset(centerX, -300f)) }
        advance(1000)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun slidingOffAfterTheLongPressKeepsTheHold() {
        button().performTouchInput { down(center) }
        advance(400)
        button().performTouchInput { moveTo(Offset(centerX, -300f)) }
        advance(500)
        assertEquals(listOf("press1"), events)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun leavingTheCompositionWhileHeldReleases() {
        button().performTouchInput { down(center) }
        advance(400)
        shown = false
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun recompositionMidHoldKeepsTheGestureAndReleasesWhatWasPressed() {
        button().performTouchInput { down(center) }
        advance(400)
        generation = 2
        advance(100)
        assertEquals(listOf("press1"), events)
        button().performTouchInput { up() }
        advance(100)
        // The release goes to the callbacks whose long press fired, so press and release always pair up.
        assertEquals(listOf("press1", "release1"), events)
        // The next hold uses the new callbacks.
        button().performTouchInput { down(center) }
        advance(400)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press1", "release1", "press2", "release2"), events)
    }

    @Test
    fun leavingTheCompositionAfterNewCallbacksReleasesWhatWasPressed() {
        button().performTouchInput { down(center) }
        advance(400)
        generation = 2
        advance(100)
        shown = false
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun recompositionBeforeTheLongPressDoesNotRestartTheTimer() {
        button().performTouchInput { down(center) }
        advance(200)
        generation = 2
        advance(200)
        assertEquals(listOf("press2"), events)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press2", "release2"), events)
    }

    @Test
    fun changingTheTimeoutMidHoldReleasesOnce() {
        button().performTouchInput { down(center) }
        advance(400)
        longPressMs = 600L
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
        button().performTouchInput { up() }
        advance(1000)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun accessibilityClickFiresThePair() {
        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        button().performSemanticsAction(SemanticsActions.OnClick)
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }

    @Test
    fun accessibilityClickDuringARealHoldIsIgnored() {
        button().performTouchInput { down(center) }
        advance(400)
        button().performSemanticsAction(SemanticsActions.OnClick)
        button().performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press1", "release1"), events)
    }
}
