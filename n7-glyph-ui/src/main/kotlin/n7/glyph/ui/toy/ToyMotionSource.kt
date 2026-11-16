package n7.glyph.ui.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface

/**
 * Feeds an engine with [Motion]. Listeners are registered on start() and removed on stop(). Delivers on the main thread.
 *
 * One [Motion] per accelerometer event, carrying the latest gyroscope reading; `timeMs` is the event timestamp in ms.
 * Missing sensors are tolerated silently; [ToySensors.NONE] registers nothing. [start] and [stop] may be called any
 * number of times, from the main thread.
 *
 * With [Facing.SCREEN] the readings follow the current display rotation, so a picture on a screen in landscape still
 * falls towards the floor. [Facing.BACK] is never rotated: the matrix does not turn with the UI.
 *
 * @param sensorHz readings per second, capped at 200 (the most an app gets without the HIGH_SAMPLING_RATE_SENSORS
 *   permission; asking for more throws in debuggable apps).
 */
public class ToyMotionSource(
    context: Context,
    private val facing: Facing,
    private val sensors: ToySensors,
    sensorHz: Int = 50,
    private val onMotion: (Motion) -> Unit,
) {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val periodUs: Int = 1_000_000 / sensorHz.coerceIn(1, MAX_HZ)
    private val handler = Handler(Looper.getMainLooper())
    private val display: Display? = if (facing == Facing.SCREEN) displayOf(context) else null

    private var running = false
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var rotation = Surface.ROTATION_0
    // A flag, not a Long.MIN_VALUE sentinel time: `t - Long.MIN_VALUE` overflows and the check would never pass.
    private var rotationKnown = false
    private var rotationCheckedNs = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Events already queued when stop() ran must not reach the engine.
            if (!running) return
            val v = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> { gx = v[0]; gy = v[1]; gz = v[2] }
                Sensor.TYPE_ACCELEROMETER -> deliver(event.timestamp, v[0], v[1], v[2])
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /** Registers the listeners. Does nothing if already started. */
    public fun start() {
        if (running || sensors == ToySensors.NONE) return
        running = true
        // The screen may have turned while stopped: read the rotation again on the first reading.
        rotationKnown = false
        register(Sensor.TYPE_ACCELEROMETER)
        if (sensors == ToySensors.ACCELEROMETER_AND_GYROSCOPE) register(Sensor.TYPE_GYROSCOPE)
    }

    /** Removes the listeners. Does nothing if not started. */
    public fun stop() {
        if (!running) return
        running = false
        try {
            manager?.unregisterListener(listener)
        } catch (_: RuntimeException) {
        }
        // A stale turn rate would otherwise ride on the first readings after the next start().
        gx = 0f
        gy = 0f
        gz = 0f
    }

    private fun register(type: Int) {
        val sensor = manager?.getDefaultSensor(type) ?: return
        try {
            manager.registerListener(listener, sensor, periodUs, handler)
        } catch (_: RuntimeException) {
            // A sensor that refuses the rate (or a restricted sensor service) counts as missing.
        }
    }

    private fun deliver(timestampNs: Long, ax: Float, ay: Float, az: Float) {
        var x = ax
        var y = ay
        var rx = gx
        var ry = gy
        if (display != null) {
            // Rotation changes rarely; reading it a few times a second is plenty.
            if (!rotationKnown || timestampNs - rotationCheckedNs > ROTATION_CHECK_NS) {
                rotation = try { display.rotation } catch (_: RuntimeException) { Surface.ROTATION_0 }
                rotationKnown = true
                rotationCheckedNs = timestampNs
            }
            // Device x/y to the axes of the rotated screen (as in the AccelerometerPlay sample); z is unchanged.
            when (rotation) {
                Surface.ROTATION_90 -> { x = -ay; y = ax; rx = -gy; ry = gx }
                Surface.ROTATION_180 -> { x = -ax; y = -ay; rx = -gx; ry = -gy }
                Surface.ROTATION_270 -> { x = ay; y = -ax; rx = gy; ry = -gx }
            }
        }
        onMotion(Motion.fromDevice(timestampNs / 1_000_000, x, y, az, rx, ry, gz, facing))
    }

    private companion object {
        const val MAX_HZ = 200
        const val ROTATION_CHECK_NS = 250_000_000L

        fun displayOf(context: Context): Display? =
            try {
                context.display
            } catch (_: UnsupportedOperationException) {
                // Not a visual context (a service, the application): use the built-in display.
                context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            }
    }
}
