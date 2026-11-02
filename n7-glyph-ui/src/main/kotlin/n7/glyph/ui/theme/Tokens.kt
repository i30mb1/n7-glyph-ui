package n7.glyph.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colour tokens of the N7 Glyph design system (mirror of tokens.json). */
public object N7Colors {
    /** Page, app and store-screenshot background. */
    public val Ground: Color = Color(0xFF000000)
    /** Cards and grouped settings on [Ground]. */
    public val Surface: Color = Color(0xFF111111)
    /** Rows inside a card, toy-list cards. */
    public val SurfaceRaised: Color = Color(0xFF1C1C1C)
    /** Decorative 1px dividers. */
    public val Hairline: Color = Color(0xFF2A2A2A)
    /** Outline of secondary buttons. */
    public val ControlBorder: Color = Color(0xFF737373)
    /** Headlines, body copy and numbers. */
    public val Ink: Color = Color(0xFFFFFFFF)
    /** Secondary copy and summaries. */
    public val InkMuted: Color = Color(0xFFA3A3A3)
    /** Mono labels and captions. */
    public val InkSubtle: Color = Color(0xFF8C8C8C)
    /** Fill of the one primary button per screen. */
    public val Primary: Color = Color(0xFFFFFFFF)
    /** Label on a [Primary] fill. */
    public val OnPrimary: Color = Color(0xFF000000)
    /** The family signature: the red dot. One per view, never text, never a fill. */
    public val Signal: Color = Color(0xFFD71921)
    /** Fully lit LED. */
    public val LedOn: Color = Color(0xFFFFFFFF)
    /** Half-lit LED. */
    public val LedMid: Color = Color(0xFFBDBDBD)
    /** Unlit LED cell. */
    public val LedOff: Color = Color(0xFF262626)
    /** Glass disc behind the matrix. */
    public val Lens: Color = Color(0xFF0A0A0A)
    /** Ring around the lens. */
    public val LensRim: Color = Color(0xFF464646)
}

/** Spacing scale. */
public object N7Spacing {
    public val Space1: Dp = 4.dp
    public val Space2: Dp = 8.dp
    public val Space3: Dp = 12.dp
    public val Space4: Dp = 16.dp
    public val Space6: Dp = 24.dp
    public val Space8: Dp = 32.dp
    public val Space12: Dp = 48.dp
}

/** Corner radii. Buttons are always pills (CircleShape). */
public object N7Radius {
    public val Small: Dp = 8.dp
    public val Card: Dp = 28.dp
}
