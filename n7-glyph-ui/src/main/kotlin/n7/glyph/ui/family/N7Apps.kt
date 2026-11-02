package n7.glyph.ui.family

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import n7.glyph.ui.R
import n7.glyph.ui.components.SectionLabel
import n7.glyph.ui.components.ToyCard
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.Frames

/** One app of the n7 Glyph family. */
public data class N7App(
    val packageName: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: List<Cell>,
    val iconSignal: List<Cell>,
)

/** Every n7 Glyph app. Add the next one here and release a new library version. */
public object N7Apps {
    public val Snake: N7App = N7App("n7.snake", R.string.n7_app_snake, R.string.n7_app_snake_summary, Frames.SnakeIcon, Frames.SnakeIconSignal)
    public val FlappyBox: N7App = N7App("n7.glyphicons", R.string.n7_app_flappy, R.string.n7_app_flappy_summary, Frames.FlappyIcon, Frames.FlappyIconSignal)

    public val all: List<N7App> = listOf(Snake, FlappyBox)

    /** The family without the app that is asking. */
    public fun others(currentPackage: String): List<N7App> = all.filter { it.packageName != currentPackage }

    /** Opens the app's Google Play page. */
    public fun openInStore(context: Context, app: N7App) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** "More from n7": the other family apps as toy cards that open Google Play. Nothing is shown if there are none. */
@Composable
public fun MoreFromN7(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = N7Apps.others(context.packageName)
    if (apps.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.n7_more_from))
        apps.forEach { app ->
            ToyCard(
                title = stringResource(app.title),
                summary = stringResource(app.summary),
                cells = app.icon,
                signal = app.iconSignal,
                modifier = Modifier.fillMaxWidth(),
                onClick = { N7Apps.openInStore(context, app) },
            )
        }
    }
}
