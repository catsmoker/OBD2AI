package com.catsmoker.obd2ai.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion language. Two springs cover every interaction: [Press] is snappy
 * and slightly under-damped so taps feel physical; [Settle] is calmer for
 * layout and state changes. Durations stay short enough that a mid-range
 * phone never stutters.
 */
object ObdMotion {
    val Press: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = 900f
    )

    val Settle: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f
    )

    val Choreography: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 260f
    )

    const val NAV_FADE_MS = 220
}

/**
 * Android's "remove animations" accessibility toggle scales the global
 * animator duration to zero. We mirror it into composition so decorative
 * motion can collapse to an instant state change without touching layout.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
