package com.catsmoker.obd2ai.ui.common

/**
 * Edge-to-edge inset handling, applied once to the activity root so every
 * screen (including future ones) stays clear of the status bar, navigation
 * bar / gesture area, and display cutouts. Existing padding is preserved and
 * insets are passed through unconsumed, so children (EditText auto-scroll,
 * IME, ScrollViews) keep working.
 */
fun android.view.View.applySystemBarInsets() {
    val startLeft = paddingLeft
    val startTop = paddingTop
    val startRight = paddingRight
    val startBottom = paddingBottom
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            startLeft + bars.left,
            startTop + bars.top,
            startRight + bars.right,
            startBottom + bars.bottom
        )
        insets
    }
}
