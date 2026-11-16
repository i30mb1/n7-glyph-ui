package n7.glyphtemplate.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import n7.glyph.ui.toy.Facing
import n7.glyph.ui.toy.ToyEngine
import n7.glyph.ui.toy.ToyHaptics
import n7.glyph.ui.toy.ToyMotionSource
import n7.glyphtemplate.BuildConfig

/**
 * The Glyph Matrix SDK side of a toy, the same in every n7 app: a subclass only returns its engine.
 *
 * The system binds the service while the toy is selected on the back of the phone. Live: a frame every [frameMs]
 * on the main thread, sensors on, haptics played. Always-on: one [ToyEngine.still] frame per EVENT_AOD, no loop.
 * Glyph Button: long press (EVENT_CHANGE) goes to [ToyEngine.onLongPress], letting go after it (EVENT_ACTION_UP) to
 * [ToyEngine.onRelease]. Every long press gets exactly one release, also when the hold is cut short by AOD or unbind.
 * Everything runs on the main looper: the engine is never called from two threads.
 *
 * Debug builds log what the phone really does under the tag GlyphToy (`adb logcat -s GlyphToy`): binds, button events
 * with hold times, and every five seconds the frame and sensor rates.
 */
abstract class GlyphToyService : Service(), GlyphMatrixManager.Callback {

    /** A fresh engine for this session of the toy. */
    protected abstract fun createEngine(): ToyEngine

    /** Time between live frames. 40 ms is 25 fps, as fast as the matrix usefully goes. */
    protected open val frameMs: Long get() = 40L

    private companion object {
        const val TAG = "GlyphToy"
        const val MAX_DT_MS = 250L
        const val STATS_MS = 5000L

        /**
         * The toy of this app that holds the SDK link right now. The SDK keeps one manager and one service connection
         * per process. If the player switches between two toys of one app and the new one is bound before the old one
         * is unbound, the new toy would never hear that it is connected, and the old one would then cut the link from
         * under it. So a newcomer first closes the link of the toy before it and then opens its own.
         */
        var owner: GlyphToyService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var glyph: GlyphMatrixManager
    private lateinit var engine: ToyEngine
    private lateinit var motion: ToyMotionSource
    private lateinit var haptics: ToyHaptics

    private var linked = false
    private var connected = false
    private var running = false
    private var holding = false
    private var lastFrameAt = 0L

    // Debug statistics only.
    private var heldSince = 0L
    private var statsAt = 0L
    private var frames = 0
    private var samples = 0

    private val toyHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what != GlyphToy.MSG_GLYPH_TOY) return@Handler false
        when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
            GlyphToy.EVENT_CHANGE -> onLongPress()
            GlyphToy.EVENT_ACTION_UP -> onButtonUp()
            GlyphToy.EVENT_AOD -> onAod()
            // GlyphToy.EVENT_ACTION_DOWN is ignored: a hold only counts from the long press.
        }
        true
    }

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!running) return
            renderLive()
            main.postDelayed(this, frameMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        owner?.takeIf { it !== this }?.closeLink(handover = true)
        owner = this
        engine = createEngine()
        motion = ToyMotionSource(this, Facing.BACK, engine.sensors, engine.sensorHz) { sample ->
            samples++
            engine.onMotion(sample)
        }
        haptics = ToyHaptics(this)
        // The application context: the manager is a process-wide singleton that keeps the first context it is given.
        glyph = GlyphMatrixManager.getInstance(applicationContext)
        glyph.init(this)
        linked = true
        debug { "bind ${javaClass.simpleName} aod=${intent?.getBooleanExtra("isAod", false)}" }
        return Messenger(toyHandler).binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        closeLink(handover = false)
        return false
    }

    /**
     * Stops the toy and gives the SDK link back. Safe to call twice. On a handover to another toy of this app the
     * matrix is left as it is: the next toy draws over it at once.
     */
    private fun closeLink(handover: Boolean) {
        stopLive()
        endHold()
        if (linked) {
            linked = false
            if (connected && !handover) {
                glyph.turnOff()
                try {
                    glyph.closeAppMatrix()
                } catch (e: Exception) {
                    Log.e(TAG, "Close failed", e)
                }
            }
            try {
                glyph.unInit()
            } catch (e: Exception) {
                Log.e(TAG, "Unlink failed", e)
            }
            debug { "unbind ${javaClass.simpleName} handover=$handover" }
        }
        connected = false
        if (owner === this) owner = null
    }

    override fun onServiceConnected(name: ComponentName?) {
        // n7 toys are drawn for the 25x25 matrix of Phone (3).
        glyph.register(Glyph.DEVICE_23112)
        try {
            glyph.setGlyphMatrixTimeout(false)
        } catch (e: Exception) {
            Log.e(TAG, "Timeout setting failed", e)
        }
        connected = true
        debug { "connected ${javaClass.simpleName}" }
        startLive()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        connected = false
        stopLive()
    }

    private fun onLongPress() {
        // A second long press without the button-up of the first (it can be lost) still gets its release first.
        endHold()
        if (!running) startLive()
        holding = true
        heldSince = SystemClock.uptimeMillis()
        debug { "long press" }
        engine.onLongPress()
    }

    private fun onButtonUp() {
        // Button-up without a long press before it is a short press: the system's, not the toy's.
        endHold()
    }

    private fun onAod() {
        stopLive()
        // Button events can be lost in always-on mode: close an open hold so the engine is not left charging.
        endHold()
        debug { "always-on frame" }
        send(engine.still())
    }

    private fun endHold() {
        if (!holding) return
        holding = false
        debug { "release after ${SystemClock.uptimeMillis() - heldSince} ms" }
        engine.onRelease()
    }

    private fun startLive() {
        if (running || !connected) return
        running = true
        motion.start()
        lastFrameAt = SystemClock.uptimeMillis()
        statsAt = lastFrameAt
        frames = 0
        samples = 0
        main.post(frameLoop)
    }

    private fun stopLive() {
        running = false
        main.removeCallbacks(frameLoop)
        if (::motion.isInitialized) motion.stop()
    }

    private fun renderLive() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameAt).coerceIn(0, MAX_DT_MS)
        lastFrameAt = now
        send(engine.frame(dt))
        // One vibration per frame, the strongest of the batch (as on the app screen's ToyStage).
        val asked = engine.drainHaptics()
        if (asked.isNotEmpty()) {
            debug { "haptics $asked" }
            haptics.play(asked)
        }
        frames++
        if (now - statsAt >= STATS_MS) {
            val seconds = (now - statsAt) / 1000f
            debug { "live: %.1f frames/s, %.0f motion samples/s".format(frames / seconds, samples / seconds) }
            statsAt = now
            frames = 0
            samples = 0
        }
    }

    private fun send(frame: IntArray) {
        if (!connected) return
        try {
            glyph.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Frame failed", e)
        }
    }

    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
