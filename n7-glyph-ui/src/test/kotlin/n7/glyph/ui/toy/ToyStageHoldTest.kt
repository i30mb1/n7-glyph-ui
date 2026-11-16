package n7.glyph.ui.toy

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import n7.glyph.ui.matrix.MatrixCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import org.robolectric.shadows.ShadowVibrator

/** Holds across pauses and engine swaps, and the haptics switch, on the real ToyStage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToyStageHoldTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class Engine(private val haptic: Haptic? = null) : ToyEngine {
        val events = mutableListOf<String>()
        var frames = 0
        override fun onLongPress() { events += "press" }
        override fun onRelease() { events += "release" }
        override fun frame(dtMs: Long): IntArray {
            frames++
            return IntArray(MatrixCanvas.SIZE * MatrixCanvas.SIZE)
        }
        override fun still(): IntArray = IntArray(MatrixCanvas.SIZE * MatrixCanvas.SIZE)
        override fun drainHaptics(): List<Haptic> = listOfNotNull(haptic)
    }

    private lateinit var sensors: ShadowSensorManager
    private val first = Engine()
    private var engine: ToyEngine by mutableStateOf(first)
    private var haptics by mutableStateOf(true)

    @Before
    fun setUp() {
        sensors = shadowOf(rule.activity.getSystemService(SensorManager::class.java))
        sensors.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        rule.mainClock.autoAdvance = false
        rule.setContent { ToyStage(engine = engine, holdLabel = "HOLD", haptics = haptics) }
        rule.waitForIdle()
    }

    private fun advance(ms: Long) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun aPauseDuringAHoldKeepsTheHoldAndReleasesOnceOnLift() {
        advance(200)
        // The test rule cannot touch a paused activity, so this gesture goes straight to the compose view.
        val view = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val centre = rule.onNodeWithText("HOLD").fetchSemanticsNode().boundsInRoot.center
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int) {
            val e = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, centre.x, centre.y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        touch(MotionEvent.ACTION_DOWN)
        advance(400)
        assertEquals(listOf("press"), first.events)

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        advance(100)
        assertTrue(sensors.listeners.isEmpty())
        val frames = first.frames
        advance(500)
        assertEquals(frames, first.frames)
        assertEquals(listOf("press"), first.events)

        touch(MotionEvent.ACTION_UP)
        advance(100)
        assertEquals(listOf("press", "release"), first.events)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(300)
        assertTrue(first.frames > frames)
        assertEquals(1, sensors.listeners.toSet().size)
        assertEquals(listOf("press", "release"), first.events)
    }

    @Test
    fun aSwapBeforeTheLongPressGivesNeitherEngineAnything() {
        advance(200)
        rule.onNodeWithText("HOLD").performTouchInput { down(center) }
        advance(200)
        val second = Engine()
        engine = second
        advance(600)
        rule.onNodeWithText("HOLD").performTouchInput { up() }
        advance(100)
        assertEquals(emptyList<String>(), first.events)
        assertEquals(emptyList<String>(), second.events)
        // The new engine's button works.
        rule.onNodeWithText("HOLD").performTouchInput { down(center) }
        advance(400)
        rule.onNodeWithText("HOLD").performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press", "release"), second.events)
    }

    @Test
    fun aShortTapNeverReachesTheEngine() {
        advance(200)
        rule.onNodeWithText("HOLD").performTouchInput { down(center) }
        advance(300)
        rule.onNodeWithText("HOLD").performTouchInput { up() }
        advance(500)
        assertEquals(emptyList<String>(), first.events)
    }

    private fun lastVibration(): Any? =
        Shadow.extract<ShadowVibrator>(rule.activity.getSystemService(VibratorManager::class.java).defaultVibrator)
            .getVibrationAttributesFromLastVibration()

    @Test
    fun hapticsPlayOnlyWhenSwitchedOn() {
        shadowOf(rule.activity.application).grantPermissions(Manifest.permission.VIBRATE)
        haptics = false
        engine = Engine(Haptic.CLICK)
        advance(300)
        assertNull(lastVibration())
        haptics = true
        advance(300)
        assertNotNull(lastVibration())
    }
}
