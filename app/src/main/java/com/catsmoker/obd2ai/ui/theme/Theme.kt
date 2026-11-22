package com.catsmoker.obd2ai.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = NothingRed,
    secondary = NothingWhite,
    tertiary = AccentTertiary,
    background = NightBackground,
    surface = NightSurface,
    surfaceVariant = NightSurfaceVariant,
    surfaceContainerLowest = NightBackground,
    surfaceContainerLow = Color(0xFF101013),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF1A1A1F),
    surfaceContainerHighest = NightSurfaceVariant,
    onPrimary = NothingWhite,
    onSecondary = NothingBlack,
    onTertiary = NothingWhite,
    onBackground = NightInk,
    onSurface = NightInk,
    onSurfaceVariant = NightInkMuted,
    outline = NothingWhite.copy(alpha = 0.20f),
    outlineVariant = NothingWhite.copy(alpha = 0.10f),
    error = Color(0xFFFF6B6B),
    onError = NothingBlack
)

private val LightScheme = lightColorScheme(
    primary = NothingRed,
    secondary = Ink,
    tertiary = InkMuted,
    background = PaperBackground,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceVariant,
    surfaceContainerLowest = NothingWhite,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = Color(0xFFF0F0F3),
    surfaceContainerHighest = PaperSurfaceVariant,
    onPrimary = NothingWhite,
    onSecondary = NothingWhite,
    onTertiary = NothingWhite,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = NothingBlack.copy(alpha = 0.14f),
    outlineVariant = NothingBlack.copy(alpha = 0.07f),
    error = Color(0xFFC62828),
    onError = NothingWhite
)

/**
 * Roles Material 3 does not name but this product needs: glass tints, the
 * ambient wash behind it, and status signals. Kept beside the color scheme
 * so light/dark and dynamic-color paths cannot drift apart.
 */
@Immutable
data class ObdExtendedColors(
    val glassTint: Color,
    val glassTintStrong: Color,
    val glassBorder: Color,
    val ambientA: Color,
    val ambientB: Color,
    val success: Color,
    val warning: Color,
    val info: Color
)

private val LightExtended = ObdExtendedColors(
    glassTint = Color(0x99FFFFFF),
    glassTintStrong = Color(0xE6FFFFFF),
    glassBorder = Color(0x40FFFFFF),
    ambientA = AmbientLightA,
    ambientB = AmbientLightB,
    success = SignalSuccess,
    warning = SignalWarning,
    info = SignalInfo
)

private val DarkExtended = ObdExtendedColors(
    glassTint = Color(0x1FFFFFFF),
    glassTintStrong = Color(0x38FFFFFF),
    glassBorder = Color(0x3DFFFFFF),
    ambientA = AmbientDarkA,
    ambientB = AmbientDarkB,
    success = SignalSuccess,
    warning = SignalWarning,
    info = SignalInfo
)

val LocalObdColors = staticCompositionLocalOf { LightExtended }

/** True on Android 12+ (API 31), where backdrop blur/RenderEffect exists. */
fun isGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Convenience accessor: `ObdTheme.colors.glassTint`. */
object ObdTheme {
    val colors: ObdExtendedColors
        @Composable get() = LocalObdColors.current
}

/**
 * App theme. [darkTheme] comes from the stored `theme_mode` preference
 * (system/light/dark), not from `isSystemInDarkTheme()` at the call site —
 * the Activity resolves it once. [dynamicColor] opts into the wallpaper
 * palette on Android 12+; the brand red stays the fallback everywhere else.
 */
@Composable
fun Obd2AiTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = remember(darkTheme, dynamicColor, context) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkScheme else LightScheme
        }
    }
    val extended = if (darkTheme) DarkExtended else LightExtended

    CompositionLocalProvider(LocalObdColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ObdTypography,
            shapes = ObdShapes,
            content = content
        )
    }
}
