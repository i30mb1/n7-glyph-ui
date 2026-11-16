package n7.glyphtemplate.toy

import kotlin.random.Random
import n7.glyph.ui.toy.ToyEngine
import n7.glyphtemplate.game.DemoEngine

/** The demo toy on the Glyph Matrix: declared in AndroidManifest.xml as one `<service>`. */
class DemoToyService : GlyphToyService() {
    override fun createEngine(): ToyEngine = DemoEngine(Random(System.nanoTime()))
}
