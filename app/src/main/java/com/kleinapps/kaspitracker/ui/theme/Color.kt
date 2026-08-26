package com.kleinapps.kaspitracker.ui.theme

import androidx.compose.ui.graphics.Color

// ----- Brand accents (shared by both themes) -----
val Violet = Color(0xFF7C5CFF)       // primary accent
val VioletDeep = Color(0xFF5E3BEE)
val VioletSoft = Color(0xFF9B84FF)   // gradient highlight / glow
val Blue = Color(0xFF448AFF)         // gradient partner for hero cards
val BlueDeep = Color(0xFF2D6BE0)
val Cyan = Color(0xFF35D2E8)         // third gradient stop, keeps the hero alive
val Lime = Color(0xFFC6FF00)         // secondary accent (dark theme highlights)

// ----- Section accents -----
// Every card used to be the same violet, which is what made the app read as
// one long grey list. Each area now owns a hue, so colour alone already tells
// you where you are.
val Mint = Color(0xFF2ED3A7)      // balances, money coming in
val Coral = Color(0xFFFF7A6B)     // spending
val Amber = Color(0xFFFFB020)     // budgets
val Pink = Color(0xFFF25CA2)      // subscriptions / recurring
val Indigo = Color(0xFF6C8BFF)    // imports, backups, plumbing

val Spend = Color(0xFFFF6B6B)   // outgoing / purchases
val Income = Color(0xFF22C55E)  // incoming
val Warning = Color(0xFFF5A524) // approaching a budget limit

/** Hero gradient, warm-to-cool so big numbers sit on a lively field, not a slab. */
val HeroGradient = listOf(VioletDeep, Violet, Blue)

/** Same gradient for bar charts, top-lit. */
val BarGradient = listOf(VioletSoft, Violet)

/** Bar of the peak day: brighter, so the week has an obvious shape. */
val BarGradientPeak = listOf(Cyan, VioletSoft)

// ----- Dark palette -----
val DarkBackground = Color(0xFF0B0D12)
val DarkSurface = Color(0xFF14171F)
val DarkSurfaceElevated = Color(0xFF1D2130)
val DarkOutline = Color(0xFF272C3A)
val DarkTextPrimary = Color(0xFFF4F5F8)
val DarkTextSecondary = Color(0xFF98A0B0)

// ----- Light palette -----
val LightBackground = Color(0xFFF3F4FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEDEFF8)
val LightOutline = Color(0xFFE2E5F0)
val LightTextPrimary = Color(0xFF14161D)
val LightTextSecondary = Color(0xFF5C6377)

val OnAccent = Color(0xFFFFFFFF)
