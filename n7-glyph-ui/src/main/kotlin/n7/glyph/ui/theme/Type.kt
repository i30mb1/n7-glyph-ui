package n7.glyph.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import n7.glyph.ui.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting): Font = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

/** Doto with round dots. Latin only: it has no Cyrillic glyphs. */
@OptIn(ExperimentalTextApi::class)
public val DotoFontFamily: FontFamily = FontFamily(
    variable(R.font.doto, 900, FontVariation.Setting("ROND", 100f)),
    variable(R.font.doto, 700, FontVariation.Setting("ROND", 100f)),
)

/** Geist: body copy, and every Cyrillic headline. */
public val GeistFontFamily: FontFamily = FontFamily(
    variable(R.font.geist, 400),
    variable(R.font.geist, 500),
    variable(R.font.geist, 600),
    variable(R.font.geist, 700),
)

/** Geist Mono: uppercase labels and captions. */
public val GeistMonoFontFamily: FontFamily = FontFamily(
    variable(R.font.geist_mono, 400),
    variable(R.font.geist_mono, 500),
)

/** Text styles of the N7 Glyph design system (mirror of tokens.json `type.groups`). */
public object N7Type {
    public val DisplayXl: TextStyle = TextStyle(fontFamily = DotoFontFamily, fontWeight = FontWeight(900), fontSize = 96.sp, lineHeight = 0.95.em)
    public val DisplayL: TextStyle = TextStyle(fontFamily = DotoFontFamily, fontWeight = FontWeight(900), fontSize = 56.sp, lineHeight = 1.em)
    public val Score: TextStyle = TextStyle(fontFamily = DotoFontFamily, fontWeight = FontWeight(900), fontSize = 48.sp, lineHeight = 1.em)
    public val DisplayS: TextStyle = TextStyle(fontFamily = DotoFontFamily, fontWeight = FontWeight(900), fontSize = 24.sp, lineHeight = 1.1.em)

    public val Headline: TextStyle = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)
    public val Title: TextStyle = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
    public val Body: TextStyle = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    public val BodyS: TextStyle = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

    public val Label: TextStyle = TextStyle(fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.12.em)
    public val Caption: TextStyle = TextStyle(fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
}
