package n7.glyph.ui.toy

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.VibratorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToyHapticsTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    private fun vibratorShadow(): ShadowVibrator =
        Shadow.extract(app.getSystemService(VibratorManager::class.java).defaultVibrator)

    @Test
    fun withoutThePermissionNothingVibratesAndNothingThrows() {
        val haptics = ToyHaptics(app)
        for (h in Haptic.entries) haptics.play(h)
        assertNull(vibratorShadow().getVibrationAttributesFromLastVibration())
    }

    @Test
    fun withThePermissionEveryHapticPlays() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val haptics = ToyHaptics(app)
        for (h in Haptic.entries) {
            ShadowVibrator.reset()
            haptics.play(h)
            assertNotNull("$h", vibratorShadow().getVibrationAttributesFromLastVibration())
        }
    }

    @Test
    fun aBatchPlaysItsStrongestOnly() {
        assertNull(ToyHaptics.strongest(emptyList()))
        assertEquals(Haptic.SUCCESS, ToyHaptics.strongest(listOf(Haptic.SUCCESS, Haptic.TICK)))
        assertEquals(Haptic.SUCCESS, ToyHaptics.strongest(listOf(Haptic.TICK, Haptic.SUCCESS, Haptic.CLICK)))
        assertEquals(Haptic.BUZZ, ToyHaptics.strongest(listOf(Haptic.THUD, Haptic.BUZZ, Haptic.CLICK, Haptic.TICK)))
        assertEquals(Haptic.THUD, ToyHaptics.strongest(listOf(Haptic.CLICK, Haptic.THUD, Haptic.TICK)))
        assertEquals(Haptic.CLICK, ToyHaptics.strongest(listOf(Haptic.TICK, Haptic.CLICK, Haptic.TICK)))
        // Of equals, the newest.
        assertEquals(Haptic.FAIL, ToyHaptics.strongest(listOf(Haptic.SUCCESS, Haptic.FAIL)))
        assertEquals(Haptic.SUCCESS, ToyHaptics.strongest(listOf(Haptic.FAIL, Haptic.SUCCESS, Haptic.BUZZ)))

        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val haptics = ToyHaptics(app)
        haptics.play(emptyList())
        assertNull(vibratorShadow().getVibrationAttributesFromLastVibration())
        haptics.play(listOf(Haptic.SUCCESS, Haptic.TICK))
        assertNotNull(vibratorShadow().getVibrationAttributesFromLastVibration())
    }

    @Test
    fun withoutAVibratorNothingVibrates() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        vibratorShadow().setHasVibrator(false)
        val haptics = ToyHaptics(app)
        for (h in Haptic.entries) haptics.play(h)
        assertNull(vibratorShadow().getVibrationAttributesFromLastVibration())
    }

    @Test
    fun aContextWithoutTheServiceOrWithABrokenOneNeverThrows() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val missing = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? = if (name == Context.VIBRATOR_MANAGER_SERVICE) null else super.getSystemService(name)
        }
        val broken = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.VIBRATOR_MANAGER_SERVICE) throw IllegalStateException("no service") else super.getSystemService(name)
        }
        for (ctx in listOf(missing, broken)) {
            val haptics = ToyHaptics(ctx)
            for (h in Haptic.entries) haptics.play(h)
        }
        assertNull(vibratorShadow().getVibrationAttributesFromLastVibration())
    }
}
