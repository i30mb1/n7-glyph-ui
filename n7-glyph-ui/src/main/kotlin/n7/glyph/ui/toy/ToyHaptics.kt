package n7.glyph.ui.toy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays [Haptic]s with the system vibrator. Does nothing without a vibrator or without the VIBRATE permission (never throws).
 *
 * The library declares no permissions: an app that wants haptics declares `android.permission.VIBRATE` itself.
 */
public class ToyHaptics(context: Context) {
    private val vibrator: Vibrator? = try {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
    } catch (_: RuntimeException) {
        null
    }

    // Checked once: VIBRATE is a normal permission, granted at install or never.
    private val allowed: Boolean =
        context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED

    /** Plays [haptic] now, cutting off whatever this app was vibrating. */
    @SuppressLint("MissingPermission") // checked in `allowed`
    public fun play(haptic: Haptic) {
        val vibrator = vibrator ?: return
        if (!allowed) return
        try {
            vibrator.vibrate(effect(haptic))
        } catch (_: RuntimeException) {
        }
    }

    /**
     * Plays one frame's batch of [haptics] (as [ToyEngine.drainHaptics] returns it) as one vibration: the strongest of
     * them. Each vibration cuts off the one before, so playing them one after another would leave only the last felt,
     * and a SUCCESS asked for together with a TICK would be lost. Strength: SUCCESS and FAIL, then BUZZ, THUD, CLICK,
     * TICK; of equals the newest. Nothing for an empty list.
     */
    public fun play(haptics: List<Haptic>) {
        strongest(haptics)?.let { play(it) }
    }

    internal companion object {
        /** The one of [haptics] that [play] plays, or null for an empty list. */
        internal fun strongest(haptics: List<Haptic>): Haptic? {
            var best: Haptic? = null
            for (h in haptics) if (best == null || strength(h) >= strength(best)) best = h
            return best
        }

        private fun strength(haptic: Haptic): Int = when (haptic) {
            Haptic.TICK -> 0
            Haptic.CLICK -> 1
            Haptic.THUD -> 2
            Haptic.BUZZ -> 3
            Haptic.SUCCESS, Haptic.FAIL -> 4
        }
    }

    private fun effect(haptic: Haptic): VibrationEffect = when (haptic) {
        Haptic.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        Haptic.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        Haptic.THUD -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        Haptic.BUZZ -> VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
        // Without amplitude control any non-zero amplitude is full strength; the rhythm still reads.
        Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 45), intArrayOf(0, 110, 0, 255), -1)
        Haptic.FAIL -> VibrationEffect.createOneShot(420, 70)
    }
}
