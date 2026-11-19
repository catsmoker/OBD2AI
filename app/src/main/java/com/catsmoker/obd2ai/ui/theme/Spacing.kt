package com.catsmoker.obd2ai.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Vertical rhythm and shared screen margins. Cards carry no outer margin of
 * their own; hosts space siblings with [CardGap].
 */
object ObdSpacing {
    /** Between two cards in the same group. */
    val CardGap = 12.dp

    /** Between two labelled sections. */
    val SectionGap = 24.dp

    /** Between a section label and the first card it introduces. */
    val LabelGap = 8.dp

    /** Horizontal screen margin for Compose screens. */
    val ScreenHorizontal = 24.dp

    /** Header row vertical padding. */
    val HeaderVertical = 12.dp

    /** Card inner padding. */
    val CardPadding = 20.dp
}
