package com.catsmoker.obd2ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive geometry: generously rounded containers, fully
 * circular controls. One family of radii everywhere — cards 28, inner
 * elements 16, chips/buttons pill-shaped — so nothing looks assembled from
 * different kits.
 */
val ObdShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Named radii for surfaces that are not Material components. */
object ObdRadius {
    /** Primary content card. */
    val Card = 28.dp

    /** Nested element inside a card (icon badge, inset panel). */
    val Inner = 16.dp

    /** Floating chrome (header bar, overlays). */
    val Bar = 32.dp

    /** Bottom-sheet style surfaces. */
    val Sheet = 36.dp

    /** Chips, buttons, badges. */
    val Pill = 100.dp
}
