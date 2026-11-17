package n7.glyphtemplate

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.random.Random
import n7.glyph.ui.components.DotHeadline
import n7.glyph.ui.components.N7Button
import n7.glyph.ui.components.N7ButtonVariant
import n7.glyph.ui.components.N7Card
import n7.glyph.ui.components.SectionLabel
import n7.glyph.ui.components.SignatureLabel
import n7.glyph.ui.family.MoreFromN7
import n7.glyph.ui.support.SupporterSection
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7GlyphTheme
import n7.glyph.ui.theme.N7Type
import n7.glyph.ui.toy.ToyStage
import n7.glyphtemplate.game.DemoEngine

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen is always black: white status and navigation bar icons, also when the phone is in light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val toyOpened = getSharedPreferences(UI_PREFS, MODE_PRIVATE).getBoolean(KEY_TOY_OPENED, false)
        setContent {
            N7GlyphTheme {
                MainScreen(toyOpenedBefore = toyOpened, onOpenGlyphToys = ::openGlyphToys)
            }
        }
    }

    private fun openGlyphToys() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit { putBoolean(KEY_TOY_OPENED, true) }
        val intent = Intent().apply {
            component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Older system versions have no Glyph Toys manager
        }
    }
}

private const val UI_PREFS = "ui"
private const val KEY_TOY_OPENED = "toy_opened"

@Composable
private fun MainScreen(toyOpenedBefore: Boolean, onOpenGlyphToys: () -> Unit) {
    var toyOpened by rememberSaveable { mutableStateOf(toyOpenedBefore) }
    val openGlyphToys = {
        onOpenGlyphToys()
        toyOpened = true
    }
    val engine = remember { DemoEngine(Random(System.nanoTime())) }

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The status bar stays outside the scroll, so scrolled cards never run under the clock.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                // The gesture bar is inside it: the content scrolls under it, and the end of the page clears it.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // The name and, right under it, one line that says what the app is: the same pair as on the store pictures.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotHeadline(lines = listOf("GLYPH", "TEMPLATE"), fontSize = 48.sp)
                Text(stringResource(R.string.tagline), style = N7Type.Body, color = N7Colors.InkMuted)
            }

            if (!toyOpened) {
                N7Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.setup_title), style = N7Type.Title, color = N7Colors.Ink)
                        Text(stringResource(R.string.setup_text), style = N7Type.BodyS, color = N7Colors.InkMuted)
                        N7Button(text = stringResource(R.string.setup_button), onClick = openGlyphToys, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // The toy itself, live: the same engine as on the back, driven by this phone's sensors and an on-screen button.
            ToyStage(
                engine = engine,
                holdLabel = stringResource(R.string.stage_hold),
                hint = stringResource(R.string.stage_hint),
                contentDescription = stringResource(R.string.stage_description),
                modifier = Modifier.fillMaxWidth(),
            )

            N7Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.how_title))
                    Step("1", stringResource(R.string.how_1))
                    Step("2", stringResource(R.string.how_2))
                    Step("3", stringResource(R.string.how_3))
                }
            }

            // App-specific cards (records, settings) go here, each an N7Card.

            if (toyOpened) {
                N7Button(
                    text = stringResource(R.string.glyph_toys),
                    variant = N7ButtonVariant.Secondary,
                    onClick = openGlyphToys,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Supporter: the coffee and the sparkles come with the library; name this app's own small extra here.
            SupporterSection(perks = listOf(stringResource(R.string.supporter_perk)))

            MoreFromN7(Modifier.fillMaxWidth())

            SignatureLabel(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = N7Type.BodyS.copy(fontFamily = N7Type.Label.fontFamily), color = N7Colors.InkSubtle)
        Text(text, style = N7Type.BodyS, color = N7Colors.InkMuted)
    }
}
