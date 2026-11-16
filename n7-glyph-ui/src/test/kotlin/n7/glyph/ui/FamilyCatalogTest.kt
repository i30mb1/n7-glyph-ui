package n7.glyph.ui

import n7.glyph.ui.family.N7Apps
import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyCatalogTest {

    @Test
    fun everyAppHasItsOwnPackageAndAPictureOnTheGrid() {
        val apps = N7Apps.all
        assertEquals(apps.size, apps.map { it.packageName }.toSet().size)
        apps.forEach { app ->
            assertTrue("${app.packageName}: empty picture", app.icon.isNotEmpty())
            assertTrue("${app.packageName}: no red element", app.iconSignal.isNotEmpty())
            (app.icon + app.iconSignal).forEach { c ->
                // Flappy Box's pipes run past the round outline on purpose; the matrix view hides those cells.
                assertTrue("${app.packageName}: (${c.x}, ${c.y}) is off the grid", c.x in 0 until GlyphMatrixSpec.SIZE && c.y in 0 until GlyphMatrixSpec.SIZE)
                assertTrue("${app.packageName}: level ${c.level}", c.level > 0f && c.level <= 1f)
            }
        }
    }

    @Test
    fun anAppDoesNotAdvertiseItself() {
        N7Apps.all.forEach { app ->
            val others = N7Apps.others(app.packageName)
            assertEquals(N7Apps.all.size - 1, others.size)
            assertTrue(others.none { it.packageName == app.packageName })
        }
    }
}
