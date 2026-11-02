package n7.glyph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    primary = N7Colors.Primary,
    onPrimary = N7Colors.OnPrimary,
    secondary = N7Colors.InkMuted,
    onSecondary = N7Colors.Ground,
    tertiary = N7Colors.Signal,
    background = N7Colors.Ground,
    onBackground = N7Colors.Ink,
    surface = N7Colors.Ground,
    onSurface = N7Colors.Ink,
    surfaceVariant = N7Colors.SurfaceRaised,
    onSurfaceVariant = N7Colors.InkMuted,
    surfaceContainer = N7Colors.Surface,
    surfaceContainerHigh = N7Colors.SurfaceRaised,
    outline = N7Colors.ControlBorder,
    outlineVariant = N7Colors.Hairline,
    error = N7Colors.Signal,
)

private val N7Typography = Typography(
    displayLarge = N7Type.DisplayXl,
    displayMedium = N7Type.DisplayL,
    displaySmall = N7Type.DisplayS,
    headlineMedium = N7Type.Headline,
    titleLarge = N7Type.Title,
    titleMedium = N7Type.Title,
    bodyLarge = N7Type.Body,
    bodyMedium = N7Type.BodyS,
    labelLarge = N7Type.BodyS,
    labelMedium = N7Type.Label,
    labelSmall = N7Type.Caption,
)

/** Wrap every screen of an n7 Glyph app in this theme. Dark only: the matrix lives on black. */
@Composable
public fun N7GlyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = N7Typography, content = content)
}
