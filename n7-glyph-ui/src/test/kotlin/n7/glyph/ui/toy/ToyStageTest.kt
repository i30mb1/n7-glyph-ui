package n7.glyph.ui.toy

import android.hardware.Sensor
import android.hardware.SensorManager
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToyStageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeEngine(override val sensors: ToySensors = ToySensors.ACCELEROMETER_AND_GYROSCOPE) : ToyEngine {
        val dts = mutableListOf<Long>()
        val motions = mutableListOf<Motion>()
        val events = mutableListOf<String>()
        var stills = 0
        var drains = 0

        override fun onLongPress() { events += "press" }
        override fun onRelease() { events += "release" }
        override fun onMotion(motion: Motion) { motions += motion }
        override fun frame(dtMs: Long): IntArray {
            dts += dtMs
            return IntArray(MatrixCanvas.SIZE * MatrixCanvas.SIZE).also { it[12 * 25 + 12] = MatrixCanvas.MAX }
        }
        override fun still(): IntArray {
            stills++
            return IntArray(MatrixCanvas.SIZE * MatrixCanvas.SIZE)
        }
        override fun drainHaptics(): List<Haptic> {
            drains++
            return listOf(Haptic.TICK)
        }
    }

    private lateinit var sensors: ShadowSensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var gyroscope: Sensor
    private val first = FakeEngine()
    private var engine: ToyEngine by mutableStateOf(first)
    private var hint by mutableStateOf("TILT.")
    private var haptics by mutableStateOf(true)
    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        val manager = rule.activity.getSystemService(SensorManager::class.java)
        sensors = shadowOf(manager)
        accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        sensors.addSensor(accelerometer)
        gyroscope = ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE)
        sensors.addSensor(gyroscope)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (shown) ToyStage(engine = engine, holdLabel = "HOLD", hint = hint, haptics = haptics)
        }
        rule.waitForIdle()
    }

    // The one listener of the running ToyMotionSource; fails when there is none or more than one.
    private fun listener() = sensors.listeners.toSet().single()

    private fun assertBothSensors() {
        val l = listener()
        assertTrue(sensors.hasListener(l, accelerometer))
        assertTrue(sensors.hasListener(l, gyroscope))
    }

    // Applies pending state changes first, so the frames in [ms] recompose with them.
    private fun advance(ms: Long) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun runsAboutThirtyFramesASecondWithRealElapsedTime() {
        assertTrue(first.stills >= 1)
        val before = first.dts.size
        advance(1000)
        val n = first.dts.size - before
        assertTrue("$n frames", n in 27..33)
        assertTrue(first.dts.drop(1).all { it in 28L..40L })
        assertEquals(first.dts.size, first.drains)
    }

    @Test
    fun sensorsRunWhileResumedAndDeliverScreenAxes() {
        advance(100)
        assertBothSensors()
        sensors.sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(accelerometer).setValues(floatArrayOf(9.8f, 0f, 0f)).setTimestamp(5_000_000_000L).build(),
        )
        val m = first.motions.single()
        assertEquals(-9.8f, m.downX, 1e-4f) // left edge down, seen from the screen
        assertEquals(5000L, m.timeMs)
    }

    @Test
    fun pausingStopsFramesAndSensorsAndResumingRestartsWithoutAJump() {
        advance(500)
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        advance(100)
        assertTrue(sensors.listeners.isEmpty())
        val paused = first.dts.size
        advance(2000)
        assertEquals(paused, first.dts.size)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(500)
        assertBothSensors()
        val after = first.dts.drop(paused)
        assertTrue(after.isNotEmpty())
        // The pause never reaches the engine as one huge step.
        assertTrue("$after", after.all { it <= 40L })
    }

    @Test
    fun recompositionDoesNotRestartTheLoop() {
        advance(200)
        val listener = listener()
        val frames = first.dts.size
        hint = "SHAKE."
        haptics = false
        advance(200)
        hint = "TILT."
        advance(200)
        assertSame(listener, listener())
        assertBothSensors()
        // Frames kept their pace through the recompositions: no restart gap, no extra short frame.
        assertTrue(first.dts.drop(frames).all { it in 28L..40L })
        // Haptics off: still drained.
        assertEquals(first.dts.size, first.drains)
    }

    @Test
    fun aNewEngineRestartsEverythingAndReleasesTheOldHold() {
        advance(200)
        val listener = listener()
        rule.onNodeWithText("HOLD").performTouchInput { down(center) }
        advance(400)
        assertEquals(listOf("press"), first.events)

        val second = FakeEngine(ToySensors.ACCELEROMETER)
        engine = second
        advance(100)
        assertEquals(listOf("press", "release"), first.events)
        val oldFrames = first.dts.size
        advance(500)
        assertEquals(oldFrames, first.dts.size)
        assertTrue(second.dts.isNotEmpty())
        assertTrue(second.stills >= 1)
        assertNotSame(listener, listener())
        assertTrue(sensors.hasListener(listener(), accelerometer))
        assertTrue(!sensors.hasListener(listener(), gyroscope))

        // Lifting the finger now reaches neither engine.
        rule.onNodeWithText("HOLD").performTouchInput { up() }
        advance(100)
        assertEquals(listOf("press", "release"), first.events)
        assertEquals(emptyList<String>(), second.events)
    }

    @Test
    fun leavingTheScreenUnregistersSensors() {
        advance(200)
        assertBothSensors()
        shown = false
        advance(100)
        assertTrue(sensors.listeners.isEmpty())
        val frames = first.dts.size
        advance(500)
        assertEquals(frames, first.dts.size)
    }

    @Test
    fun noSensorsRegistersNothing() {
        val quiet = FakeEngine(ToySensors.NONE)
        engine = quiet
        advance(300)
        assertTrue(sensors.listeners.isEmpty())
        assertTrue(quiet.dts.isNotEmpty())
    }
}
