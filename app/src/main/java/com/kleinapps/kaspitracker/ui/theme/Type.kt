package com.kleinapps.kaspitracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kleinapps.kaspitracker.R

// Variable fonts bundled in res/font (SIL Open Font License). Each Font entry
// pins one weight; Literata also its optical size, which is what gives the
// big amounts their bookish character.
@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, opticalSize: Float) = Font(
    R.font.literata,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize)
    )
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Literata — amounts and headings. Large optical size for display numbers. */
val LiterataDisplay = FontFamily(literata(500, 48f))
val Literata = FontFamily(literata(500, 24f), literata(600, 24f))

/** Onest — everything else in the interface. */
val Onest = FontFamily(
    variable(R.font.onest, 400),
    variable(R.font.onest, 500),
    variable(R.font.onest, 600),
    variable(R.font.onest, 700)
)

/** JetBrains Mono — only the notification log and pairing codes. */
val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500)
)

/** Lining tabular figures: amounts line up digit for digit. */
private const val TABULAR = "tnum, lnum"

/** The design's type scale (Kaspi Tracker Tokens). */
@Immutable
data class KaspiType(
    val display: TextStyle = TextStyle(
        fontFamily = LiterataDisplay, fontWeight = FontWeight(500),
        fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight(500),
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.015).em
    ),
    val titleL: TextStyle = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight(500),
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    val amountM: TextStyle = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight(500),
        fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR
    ),
    val titleM: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(600),
        fontSize = 17.sp, lineHeight = 24.sp
    ),
    val bodyL: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(500),
        fontSize = 15.sp, lineHeight = 20.sp
    ),
    val bodyM: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    val bodyS: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(400),
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    val label: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(500),
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    val overline: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(600),
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em
    ),
    /** Amounts inside rows: Onest, tabular. */
    val money: TextStyle = TextStyle(
        fontFamily = Onest, fontWeight = FontWeight(600),
        fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight(400),
        fontSize = 12.sp, lineHeight = 18.sp
    )
)

val LocalKaspiType = staticCompositionLocalOf { KaspiType() }

/** Material slots mapped onto the same scale, for stock components (fields, dialogs, chips). */
fun materialTypography(t: KaspiType) = Typography(
    displayLarge = t.display,
    displayMedium = t.display,
    displaySmall = t.headline,
    headlineLarge = t.headline,
    headlineMedium = t.headline,
    headlineSmall = t.titleL,
    titleLarge = t.titleL,
    titleMedium = t.titleM,
    titleSmall = t.bodyL.copy(fontWeight = FontWeight(600)),
    bodyLarge = t.bodyL.copy(fontWeight = FontWeight(400)),
    bodyMedium = t.bodyM,
    bodySmall = t.bodyS,
    labelLarge = t.bodyM.copy(fontWeight = FontWeight(600)),
    labelMedium = t.label,
    labelSmall = t.overline
)
