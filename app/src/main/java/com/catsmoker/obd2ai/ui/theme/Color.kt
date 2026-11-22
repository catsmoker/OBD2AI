package com.catsmoker.obd2ai.ui.theme

import androidx.compose.ui.graphics.Color

// Nothing-inspired monochrome + signal-red brand. The palette stays
// deliberately small: neutrals carry structure, red carries meaning.
val NothingBlack = Color(0xFF000000)
val NothingWhite = Color(0xFFFFFFFF)
val NothingRed = Color(0xFFEB0000)
val NothingGrey = Color(0xFF121212)
val NothingGreyLight = Color(0xFF1E1E1E)

// Paper light theme.
val PaperBackground = Color(0xFFF5F5F7)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFECECEF)
val Ink = Color(0xFF111114)
val InkMuted = Color(0xFF5B5B63)

// Dark theme surfaces, one step off pure black so cards read as layers.
val NightBackground = Color(0xFF08080A)
val NightSurface = Color(0xFF17171B)
val NightSurfaceVariant = Color(0xFF232329)
val NightInk = Color(0xFFF5F5F7)
val NightInkMuted = Color(0xFFA3A3AD)

// Press feedback: buttons tint while the finger is down.
val PressPink = Color(0xFFFF999C)

val AccentPrimary = NothingRed
val AccentTertiary = NothingGreyLight

// Status colors. Never the only signal — every use pairs color with text.
val SignalSuccess = Color(0xFF2FBF71)
val SignalWarning = Color(0xFFF0A63C)
val SignalInfo = Color(0xFF4C8DFF)

/** Soft, low-alpha ambience painted behind the whole app so glass has depth to refract. */
val AmbientLightA = Color(0x14EB0000)
val AmbientLightB = Color(0x0F4C8DFF)
val AmbientDarkA = Color(0x2EEB0000)
val AmbientDarkB = Color(0x1F4C8DFF)

// Instrument panel: a fixed engraved face, independent of light/dark pages.
val PanelInk = Color(0xFF202823)
val PanelPaper = Color(0xFFF4F6E9)
val Phosphor = Color(0xFFD4FA68)
