package com.catsmoker.obd2ai.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdTheme

/**
 * The app-wide backdrop that glass surfaces sample. Null outside
 * [AppBackdropHost] (and on devices where we cannot blur), in which case
 * [glass] degrades to a solid, still-legible translucent surface.
 */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Liquid Glass, the Apple way: a soft ambient wash behind everything, then
 * content, recorded once into a backdrop layer that glass surfaces sample.
 *
 * The wash is what makes glass legible as glass — blurring a flat background
 * reads as a plain grey card. It is deliberately faint: two large radial
 * tints, no hard edges, no per-frame animation.
 */
@Composable
fun AppBackdropHost(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Default onDraw is drawContent(); the wash is drawn inside the layer, so
    // the recorded backdrop includes it and no lambda keys are re-created per
    // recomposition.
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
        // Only the wash is recorded into the backdrop layer, never the glass
        // surfaces that sample it: a layer that contains its own consumers
        // forms a render-node cycle (HWUI recurses until the stack dies).
        Box(modifier.fillMaxSize().background(background)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                AmbientWash()
            }
            content()
        }
    }
}

/** Faint brand-tinted wash; painted over the window background. */
@Composable
private fun AmbientWash() {
    val colors = ObdTheme.colors
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.ambientA, Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.06f),
                    radius = w * 0.9f
                ),
                radius = w * 0.9f,
                center = Offset(w * 0.12f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.ambientB, Color.Transparent),
                    center = Offset(w * 0.95f, h * 0.55f),
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.95f, h * 0.55f)
            )
        }
    }
}

/**
 * Liquid Glass surface modifier.
 *
 * Effect order follows the library contract: color filter → blur → lens.
 * Lens refraction needs API 33 and a corner-based shape; blur needs API 31.
 * When either is unavailable we keep the same layout and fall back to a
 * translucent tint with a hairline border, so nothing shifts or disappears.
 *
 * @param tint surface wash drawn over the refracted backdrop
 * @param blurPx gaussian radius; keep small (8–20) for legibility and GPU cost
 * @param refraction refraction strength in dp; null disables the lens
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(ObdRadius.Card),
    tint: Color? = null,
    blurPx: Dp = 16.dp,
    refraction: Dp? = 18.dp,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 0.dp
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val colors = ObdTheme.colors
    val surface = tint ?: colors.glassTint
    val border = colors.glassBorder

    val shadowed = if (elevation > 0.dp) {
        shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color.Black.copy(alpha = 0.22f)
        )
    } else {
        Modifier
    }

    val canGlass = backdrop != null && isRenderEffectSupported()
    if (!canGlass) {
        // Fallback: solid enough to read over any background, same geometry.
        return shadowed
            .clip(shape)
            .background(surface)
            .border(borderWidth, border, shape)
    }

    val lensAmount = refraction
    val hasLens = lensAmount != null && lensAmount > 0.dp && isRuntimeShaderSupported() &&
        shape is CornerBasedShape

    return shadowed.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurPx.toPx())
            if (hasLens && lensAmount != null) {
                lens(
                    refractionHeight = lensAmount.toPx(),
                    refractionAmount = (lensAmount * 1.6f).toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            }
        },
        highlight = null,
        shadow = null,
        onDrawSurface = {
            drawRect(surface)
            // Light sheen from the top edge: the "polished" part of the glass.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (colors.glassTint.alpha > 0.5f) 0.30f else 0.10f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.55f
                )
            )
        },
        onDrawFront = {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, color = border, style = Stroke(borderWidth.toPx()))
        }
    )
}

/**
 * Convenience: a glass card with the standard inner padding. Use it for
 * floating/overlay surfaces and the few hero panels — not for every card in
 * a scrolling list, where a tonal surface is cheaper and calmer.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color? = null,
    blurPx: Dp = 16.dp,
    refraction: Dp? = 18.dp,
    elevation: Dp = 0.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .glass(shape = shape, tint = tint, blurPx = blurPx, refraction = refraction, elevation = elevation)
            .padding(contentPadding),
        content = content
    )
}

/** Pill-shaped glass chip/button background, used by the icons in the chrome. */
@Composable
fun Modifier.glassPill(
    tint: Color? = null,
    blurPx: Dp = 10.dp,
    elevation: Dp = 0.dp
): Modifier = glass(
    shape = RoundedCornerShape(ObdRadius.Pill),
    tint = tint,
    blurPx = blurPx,
    refraction = 10.dp,
    elevation = elevation
)
