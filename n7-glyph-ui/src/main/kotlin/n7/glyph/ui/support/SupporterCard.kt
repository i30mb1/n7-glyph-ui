package n7.glyph.ui.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import n7.glyph.ui.R
import n7.glyph.ui.components.GlyphMatrix
import n7.glyph.ui.components.N7Button
import n7.glyph.ui.components.N7ButtonVariant
import n7.glyph.ui.components.N7Card
import n7.glyph.ui.components.SectionLabel
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type

/**
 * The Supporter card, the same in every n7 app: the steaming cup on a small matrix, what the purchase gives (the
 * thank-you on the matrix and the app's own [perks]) and the button, or the thanks once it is bought.
 * @param price from [Supporter.price]; null shows "coming soon" and a disabled button.
 */
@Composable
public fun SupporterCard(
    owned: Boolean,
    price: String?,
    perks: List<String>,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    N7Card(modifier.fillMaxWidth()) {
        SupporterContent(owned, price, perks, onBuy, buyVariant = N7ButtonVariant.Secondary)
    }
}

/** What the card and the dialog share: label, pitch or thanks, the cup, the perks and the buy button. */
@Composable
private fun SupporterContent(
    owned: Boolean,
    price: String?,
    perks: List<String>,
    onBuy: () -> Unit,
    buyVariant: N7ButtonVariant,
    after: @Composable () -> Unit = {},
) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.n7_supporter_title))
                Text(
                    stringResource(if (owned) R.string.n7_supporter_thanks else R.string.n7_supporter_pitch),
                    style = if (owned) N7Type.Title else N7Type.BodyS,
                    color = if (owned) N7Colors.Ink else N7Colors.InkMuted,
                )
            }
            Box(Modifier.size(88.dp)) { Coffee() }
        }
        (listOf(stringResource(R.string.n7_supporter_perk_matrix)) + perks).forEach { Perk(it) }
        if (!owned) {
            N7Button(
                text = if (price != null) stringResource(R.string.n7_supporter_buy, price) else stringResource(R.string.n7_supporter_unavailable),
                variant = buyVariant,
                enabled = price != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = onBuy,
            )
        }
        after()
    }
}

/**
 * The supporter button for the top-right corner of the first screen, the same in every n7 app (first in Glyph Pet):
 * a dollar in a ring, filled once the player supports n7.
 */
@Composable
public fun SupporterButton(owned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (owned) N7Colors.Ink else N7Colors.SurfaceRaised)
            .border(1.dp, N7Colors.ControlBorder, CircleShape)
            .clickable(onClickLabel = stringResource(R.string.n7_supporter_title), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$", style = N7Type.Title, color = if (owned) N7Colors.SurfaceRaised else N7Colors.Ink)
    }
}

/** The [SupporterCard] content in a dialog, with a Close button under it. */
@Composable
public fun SupporterDialog(owned: Boolean, price: String?, perks: List<String>, onBuy: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        N7Card(Modifier.fillMaxWidth()) {
            SupporterContent(owned, price, perks, onBuy, buyVariant = N7ButtonVariant.Primary) {
                N7Button(
                    text = stringResource(R.string.n7_supporter_close),
                    variant = N7ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * [SupporterButton] wired to [supporter]: a tap opens [SupporterDialog] with the price and the Play purchase sheet.
 * Put it at the end of the row that holds the app's name.
 */
@Composable
public fun SupporterCorner(perks: List<String>, modifier: Modifier = Modifier, supporter: Supporter = rememberSupporter()) {
    val context = LocalContext.current
    val owned by supporter.owned.collectAsState()
    val price by supporter.price.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    SupporterButton(owned = owned, onClick = { open = true }, modifier = modifier)
    if (open) {
        SupporterDialog(
            owned = owned,
            price = price,
            perks = perks,
            onBuy = {
                open = false
                context.findActivity()?.let(supporter::buy)
            },
            onDismiss = { open = false },
        )
    }
}

/** The thank-you cup, looping. */
@Composable
private fun Coffee() {
    var t by remember { mutableLongStateOf(600L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            // Loop the steady middle of the animation: no fade in or out on the card.
            t = if (t >= SupporterGlyphs.COFFEE_MS - 600) 600 else t + 80
        }
    }
    val frame = SupporterGlyphs.coffee(t)
    val cells = buildList {
        for (i in frame.indices) if (frame[i] > 0) add(Cell(i % GlyphMatrixSpec.SIZE, i / GlyphMatrixSpec.SIZE, (frame[i] / 2000f).coerceIn(0.15f, 1f)))
    }
    GlyphMatrix(cells = cells, modifier = Modifier.size(88.dp), glow = false)
}

@Composable
private fun Perk(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✦", style = N7Type.BodyS.copy(fontFamily = N7Type.Label.fontFamily), color = N7Colors.InkSubtle)
        Text(text, style = N7Type.BodyS, color = N7Colors.InkMuted)
    }
}

/**
 * A [Supporter] for this screen: connected to Play while the screen is resumed, disconnected when it leaves.
 * Read [Supporter.owned] from it to switch the app's own extra on.
 */
@Composable
public fun rememberSupporter(): Supporter {
    val context = LocalContext.current
    val supporter = remember { Supporter(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) supporter.connect() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            supporter.disconnect()
        }
    }
    return supporter
}

/** [SupporterCard] wired to [supporter]: price, purchase state and the Play purchase sheet. */
@Composable
public fun SupporterSection(perks: List<String>, modifier: Modifier = Modifier, supporter: Supporter = rememberSupporter()) {
    val context = LocalContext.current
    val owned by supporter.owned.collectAsState()
    val price by supporter.price.collectAsState()
    SupporterCard(
        owned = owned,
        price = price,
        perks = perks,
        onBuy = { context.findActivity()?.let(supporter::buy) },
        modifier = modifier,
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
