package n7.glyph.ui.family

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import n7.glyph.ui.R
import n7.glyph.ui.components.N7PagerDots
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

    public val Pet: N7App = N7App("n7.glyphpet", R.string.n7_app_pet, R.string.n7_app_pet_summary, FamilyIcons.Pet, FamilyIcons.PetSignal)
    public val Flame: N7App = N7App("n7.glyphflame", R.string.n7_app_flame, R.string.n7_app_flame_summary, FamilyIcons.Flame, FamilyIcons.FlameSignal)
    public val Safe: N7App = N7App("n7.glyphsafe", R.string.n7_app_safe, R.string.n7_app_safe_summary, FamilyIcons.Safe, FamilyIcons.SafeSignal)
    public val Challenge: N7App = N7App("n7.glyphchallenge", R.string.n7_app_challenge, R.string.n7_app_challenge_summary, FamilyIcons.Challenge, FamilyIcons.ChallengeSignal)
    public val Party: N7App = N7App("n7.glyphparty", R.string.n7_app_party, R.string.n7_app_party_summary, FamilyIcons.Party, FamilyIcons.PartySignal)
    public val Guess: N7App = N7App("n7.glyphguess", R.string.n7_app_guess, R.string.n7_app_guess_summary, FamilyIcons.Guess, FamilyIcons.GuessSignal)
    public val Fishing: N7App = N7App("n7.glyphfishing", R.string.n7_app_fishing, R.string.n7_app_fishing_summary, FamilyIcons.Fishing, FamilyIcons.FishingSignal)

    /** Every family app on Google Play: a card opens its store page. */
    public val all: List<N7App> = listOf(Pet, Snake, FlappyBox, Flame, Safe, Challenge, Party, Guess, Fishing)

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

/**
 * "More from n7": the other family apps, one toy card at a time. The cards are swiped sideways and a row of dots
 * under them shows how many there are and which one is on screen. A card opens the app's Google Play page.
 * Nothing is shown if there are no other apps.
 */
@Composable
public fun MoreFromN7(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = N7Apps.others(context.packageName)
    if (apps.isEmpty()) return
    val pager = rememberPagerState(pageCount = { apps.size })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.n7_more_from))
        Box {
            // The tallest card decides the height, so the row does not jump while swiping. These copies are
            // never seen, never clicked and never read aloud.
            apps.forEach { app ->
                ToyCard(
                    title = stringResource(app.title),
                    summary = stringResource(app.summary),
                    cells = emptyList(),
                    modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {},
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.matchParentSize(), pageSpacing = 8.dp) { page ->
                val app = apps[page]
                ToyCard(
                    title = stringResource(app.title),
                    summary = stringResource(app.summary),
                    cells = app.icon,
                    signal = app.iconSignal,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { N7Apps.openInStore(context, app) },
                )
            }
        }
        if (apps.size > 1) N7PagerDots(count = apps.size, current = pager.currentPage, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}
