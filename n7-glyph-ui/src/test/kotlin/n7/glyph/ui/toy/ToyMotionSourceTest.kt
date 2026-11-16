package n7.glyph.ui.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.shadows.ShadowSensor
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToyMotionSourceTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val sensors = shadowOf(context.getSystemService(SensorManager::class.java))
    private val accelerometer: Sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor = ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE)
    private val got = mutableListOf<Motion>()

    private fun source(facing: Facing = Facing.BACK, kind: ToySensors = ToySensors.ACCELEROMETER_AND_GYROSCOPE) =
        ToyMotionSource(context, facing, kind, 100) { got += it }

    private fun event(sensor: Sensor, x: Float, y: Float, z: Float, timeNs: Long = 1_000_000_000L): SensorEvent =
        SensorEventBuilder.newBuilder().setSensor(sensor).setValues(floatArrayOf(x, y, z)).setTimestamp(timeNs).build()

    private fun listener(): SensorEventListener = sensors.listeners.toSet().single()

    @Test
    fun noSensorsAtAllIsTolerated() {
        val s = source()
        s.start()
        assertTrue(sensors.listeners.isEmpty())
        s.stop()
        s.stop()
        assertTrue(got.isEmpty())
    }

    @Test
    fun aMissingGyroscopeStillGivesTilt() {
        sensors.addSensor(accelerometer)
        val s = source()
        s.start()
        assertTrue(sensors.hasListener(listener(), accelerometer))
        sensors.sendSensorEventToListeners(event(accelerometer, 0f, 9.8f, 0f, 3_000_000L))
        val m = got.single()
        assertEquals(9.8f, m.downY, 1e-4f)
        assertEquals(0f, m.spin, 0f)
        assertEquals(0f, m.wobble, 0f)
        assertEquals(3L, m.timeMs)
        s.stop()
        assertTrue(sensors.listeners.isEmpty())
    }

    @Test
    fun oneMotionPerAccelerometerEventCarryingTheLatestTurn() {
        sensors.addSensor(accelerometer)
        sensors.addSensor(gyroscope)
        val s = source(Facing.BACK)
        s.start()
        val l = listener()
        assertTrue(sensors.hasListener(l, accelerometer))
        assertTrue(sensors.hasListener(l, gyroscope))
        l.onSensorChanged(event(gyroscope, 1f, 2f, 3f))
        l.onSensorChanged(event(gyroscope, 3f, 4f, 0.5f))
        assertTrue("a gyroscope reading alone delivers nothing", got.isEmpty())
        l.onSensorChanged(event(accelerometer, 0f, 9.8f, 0f, 7_999_999L))
        l.onSensorChanged(event(accelerometer, 0f, 9.8f, 0f, 9_000_000L))
        assertEquals(2, got.size)
        assertEquals(0.5f, got[0].spin, 1e-6f)
        assertEquals(5f, got[0].wobble, 1e-6f)
        assertEquals(7L, got[0].timeMs)
        assertEquals(9L, got[1].timeMs)
        s.stop()
    }

    @Test
    fun accelerometerOnlyNeverRegistersTheGyroscope() {
        sensors.addSensor(accelerometer)
        sensors.addSensor(gyroscope)
        val s = source(kind = ToySensors.ACCELEROMETER)
        s.start()
        assertTrue(sensors.hasListener(listener(), accelerometer))
        assertFalse(sensors.hasListener(listener(), gyroscope))
        s.stop()
    }

    @Test
    fun noneRegistersNothing() {
        sensors.addSensor(accelerometer)
        sensors.addSensor(gyroscope)
        val s = source(kind = ToySensors.NONE)
        s.start()
        assertTrue(sensors.listeners.isEmpty())
        s.stop()
    }

    @Test
    fun startTwiceRegistersOnceAndStopDropsLateEventsAndTheOldTurn() {
        sensors.addSensor(accelerometer)
        sensors.addSensor(gyroscope)
        val s = source(Facing.SCREEN)
        s.start()
        s.start()
        val l = listener()
        l.onSensorChanged(event(gyroscope, 0f, 0f, 2f))
        s.stop()
        assertTrue(sensors.listeners.isEmpty())
        // An event already on its way when stop() ran.
        l.onSensorChanged(event(accelerometer, 0f, 9.8f, 0f))
        assertTrue(got.isEmpty())
        s.start()
        listener().onSensorChanged(event(accelerometer, 0f, 9.8f, 0f))
        assertEquals(0f, got.single().spin, 0f)
        s.stop()
    }

    @Test
    fun theBackIsNeverRotatedWithTheScreen() {
        shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        sensors.addSensor(accelerometer)
        val s = source(Facing.BACK, ToySensors.ACCELEROMETER)
        s.start()
        listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, 1_000_000_000L))
        listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, 2_000_000_000L))
        for (m in got) {
            assertEquals(9.8f, m.downX, 1e-4f)
            assertEquals(0f, m.downY, 1e-4f)
        }
        s.stop()
    }

    @Test
    fun theScreenFollowsTheDisplayRotationFromTheFirstReading() {
        val display = shadowOf(ShadowDisplay.getDefaultDisplay())
        sensors.addSensor(accelerometer)
        sensors.addSensor(gyroscope)
        val s = source(Facing.SCREEN, ToySensors.ACCELEROMETER_AND_GYROSCOPE)
        // Left edge of the phone down (ax = +9.8), turning around the device x axis (gx = 1).
        // Seen on a screen turned with the phone, "down" is the bottom of the picture in landscape.
        val cases = listOf(
            Surface.ROTATION_0 to Pair(-9.8f, 0f),
            Surface.ROTATION_90 to Pair(0f, 9.8f),
            Surface.ROTATION_180 to Pair(9.8f, 0f),
            Surface.ROTATION_270 to Pair(0f, -9.8f),
        )
        var t = 1_000_000_000L
        for ((rotation, down) in cases) {
            display.setRotation(rotation)
            // A fresh start reads the rotation at once.
            s.start()
            listener().onSensorChanged(event(gyroscope, 1f, 0f, 0f, t))
            listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, t))
            val m = got.last()
            assertEquals("rotation $rotation", down.first, m.downX, 1e-4f)
            assertEquals("rotation $rotation", down.second, m.downY, 1e-4f)
            s.stop()
            t += 10_000_000L
        }
        // While running, a new rotation is picked up within a fraction of a second.
        display.setRotation(Surface.ROTATION_0)
        s.start()
        listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, 5_000_000_000L))
        assertEquals(-9.8f, got.last().downX, 1e-4f)
        display.setRotation(Surface.ROTATION_90)
        listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, 5_100_000_000L))
        assertEquals("not read again this soon", -9.8f, got.last().downX, 1e-4f)
        listener().onSensorChanged(event(accelerometer, 9.8f, 0f, 0f, 5_400_000_000L))
        assertEquals(0f, got.last().downX, 1e-4f)
        assertEquals(9.8f, got.last().downY, 1e-4f)
        s.stop()
    }

    @Test
    fun forceOfAShakeIsTheLengthOfTheReading() {
        sensors.addSensor(accelerometer)
        val s = source(Facing.SCREEN, ToySensors.ACCELEROMETER)
        s.start()
        listener().onSensorChanged(event(accelerometer, 3f, 4f, 12f))
        assertEquals(13f, got.single().force, 1e-4f)
        assertEquals(sqrt(9f + 16f + 144f), got.single().force, 1e-4f)
        s.stop()
    }
}
