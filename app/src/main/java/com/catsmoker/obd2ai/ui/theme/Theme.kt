package com.catsmoker.obd2ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = AccentPrimary,
    secondary = NothingWhite,
    tertiary = AccentTertiary,
    background = NothingBlack,
    surface = NothingGrey,
    surfaceVariant = NothingGreyLight,
    onPrimary = NothingWhite,
    onSecondary = NothingBlack,
    onTertiary = NothingWhite,
    onBackground = NothingWhite,
    onSurface = NothingWhite,
    onSurfaceVariant = NothingWhite.copy(alpha = 0.7f),
    outline = NothingWhite.copy(alpha = 0.12f),
    outlineVariant = NothingWhite.copy(alpha = 0.06f)
)

private val LightScheme = lightColorScheme(
    primary = AccentPrimary,
    secondary = Ink,
    tertiary = InkMuted,
    background = PaperBackground,
    surface = NothingWhite,
    surfaceVariant = PaperSurfaceVariant,
    onPrimary = NothingWhite,
    onSecondary = NothingWhite,
    onTertiary = NothingWhite,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = NothingBlack.copy(alpha = 0.12f),
    outlineVariant = NothingBlack.copy(alpha = 0.06f)
)

/**
 * App theme. [darkTheme] comes from the stored `theme_mode` preference
 * (system/light/dark), not from `isSystemInDarkTheme()` at the call site —
 * the Activity resolves it once.
 */
@Composable
fun Obd2AiTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ObdTypography,
        shapes = ObdShapes,
        content = content
    )
}
