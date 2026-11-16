package n7.glyphtemplate

import java.io.File
import kotlin.random.Random
import n7.glyph.ui.matrix.MatrixCanvas
import n7.glyph.ui.toy.Motion
import n7.glyphtemplate.game.DemoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the pictures of the store listing and the launcher icon with the REAL engine and writes them to
 * `app/build/store/frames.json` (`{"name": [625 ints], ...}`), which the store kit (`n7-glyph-ui/store/render.py`) reads.
 * Also writes `frames.txt`, an ASCII dump of the same frames: if a picture is not recognisable there, it will not be on the phone.
 */
class StoreFramesTest {

    private fun frames(): Map<String, IntArray> = linkedMapOf(
        // The icon and the toy preview: the ball at rest in the middle.
        "icon" to DemoEngine(Random(1)).still(),
        // Tilted: the ball has rolled down to the right edge.
        "roll" to DemoEngine(Random(1)).run {
            onMotion(Motion(0, 6f, 7.5f, 0f))
            repeat(40) { frame(40) }
            frame(40)
        },
        // Holding the button: the rim is half charged.
        "charge" to DemoEngine(Random(1)).run {
            onMotion(Motion(0, 0f, 0f, -Motion.G))
            onLongPress()
            repeat(10) { frame(60) }
            frame(0)
        },
        // Let go: the ring flies out.
        "ring" to DemoEngine(Random(1)).run {
            onMotion(Motion(0, 0f, 0f, -Motion.G))
            onLongPress()
            repeat(25) { frame(60) }
            onRelease()
            frame(220)
        },
    )

    @Test
    fun `writes the store frames`() {
        val frames = frames()
        val dir = File(moduleDir(), "build/store").apply { mkdirs() }
        val json = frames.entries.joinToString(",\n", "{\n", "\n}\n") { (name, frame) ->
            "  \"$name\": [${frame.joinToString(",")}]"
        }
        File(dir, "frames.json").writeText(json)
        File(dir, "frames.txt").writeText(frames.entries.joinToString("\n") { (name, frame) -> "$name\n${ascii(frame)}" })
        print(File(dir, "frames.txt").readText())

        for ((name, frame) in frames) {
            assertEquals(name, 625, frame.size)
            assertTrue("$name is empty", frame.any { it > 0 })
            assertTrue("$name out of range", frame.all { it in 0..MatrixCanvas.MAX })
        }
    }

    /** The app module: Gradle runs unit tests in it, an IDE may run them from the project root. */
    private fun moduleDir(): File {
        val here = File(System.getProperty("user.dir") ?: ".")
        return if (File(here, "src/main/AndroidManifest.xml").exists()) here else File(here, "app")
    }

    /** `#` above 60% brightness, `+` above 25%, `.` dimmer but lit, space off. */
    private fun ascii(frame: IntArray): String = buildString {
        for (y in 0 until MatrixCanvas.SIZE) {
            for (x in 0 until MatrixCanvas.SIZE) {
                val v = frame[y * MatrixCanvas.SIZE + x]
                append(
                    when {
                        v > MatrixCanvas.MAX * 0.6 -> '#'
                        v > MatrixCanvas.MAX * 0.25 -> '+'
                        v > 0 -> '.'
                        else -> ' '
                    },
                )
            }
            append('\n')
        }
    }
}
