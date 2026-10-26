package com.kleinapps.kaspitracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kleinapps.kaspitracker.data.Category

/**
 * Warm paper instead of bank blue. Surfaces are told apart by tone alone — no
 * borders, no shadows — and hierarchy comes from size, weight and ink colour.
 * The accent is the colour of the person whose numbers are on screen.
 *
 * Contrast (checked in the design file): ink on bg 13.9:1 / 14.6:1, ink2
 * 6.6:1 / 8.4:1, ink3 4.6:1 / 5.6:1; person and category colours ≥ 3:1 on
 * surf in both themes.
 */
@Immutable
data class KaspiColors(
    val dark: Boolean,
    /** Screen background. */
    val bg: Color,
    /** Cards, sheets, the bottom bar. */
    val surf: Color,
    /** Tracks, chips, field fills, skeletons. */
    val surf2: Color,
    /** Dividers and field outlines. */
    val line: Color,
    /** Main text, and spending amounts. */
    val ink: Color,
    val ink2: Color,
    /** Hints and disabled text. */
    val ink3: Color,
    val press: Color,
    val scrim: Color,
    /** Income, success. */
    val pos: Color,
    /** Deleting, errors, overspending. */
    val neg: Color,
    /** Warnings and banners. */
    val warn: Color,
    /** Text on an accent fill. */
    val onAcc: Color,
    /** The other person and "us" — the user's own colour is a [PersonTone]. */
    val her: Color,
    val us: Color,
    /** Accent of the current view: mine, hers or ours. */
    val accent: Color,
    private val categories: List<Color>
) {
    fun category(category: Category): Color = categories[category.ordinal]

    /** Accent mixed into the card surface — selected chips, soft buttons. */
    val accentSoft: Color get() = accent.copy(alpha = 0.14f).compositeOver(surf)

    /** Icon tile behind a category or tool glyph: its colour at 16 % over the card. */
    fun tint(color: Color, alpha: Float = 0.16f): Color = color.copy(alpha = alpha).compositeOver(surf)
}

/** A person's colour. Each user picks theirs; the partner's arrives with their profile. */
enum class PersonTone(val light: Color, val dark: Color) {
    CLAY(Color(0xFFB84E2A), Color(0xFFEC8A62)),
    TEAL(Color(0xFF22767A), Color(0xFF62C2C4)),
    BLUE(Color(0xFF3A64A8), Color(0xFF86A9E6)),
    GREEN(Color(0xFF3D7A36), Color(0xFF8CCB7F)),

    /** Not offered as a choice: used for the partner when both picked the same colour. */
    PLUM(Color(0xFF84469A), Color(0xFFC48BD6));

    fun color(dark: Boolean): Color = if (dark) this.dark else light

    companion object {
        /** The ones offered on the pairing screen. */
        val choices: List<PersonTone> = listOf(CLAY, TEAL, BLUE, GREEN)

        fun of(index: Int): PersonTone = choices.getOrElse(index) { CLAY }
    }
}

/** Colour used for "us" — gold in both themes. */
private val UsLight = Color(0xFF86600F)
private val UsDark = Color(0xFFE0B04F)

// Category colours c1…c11, in [Category] declaration order.
private val CategoriesLight = listOf(
    0xFF5B8A2B, 0xFFC0641F, 0xFF3767A0, 0xFFB03F78, 0xFF21857B, 0xFF7350AD,
    0xFF9A7710, 0xFF4858B5, 0xFF7A6C60, 0xFF2D7A4B, 0xFF857A70
).map(::Color)
private val CategoriesDark = listOf(
    0xFF9CC56E, 0xFFEE9A5C, 0xFF83ADE0, 0xFFE485B5, 0xFF6CC7BC, 0xFFB69BE6,
    0xFFDCC056, 0xFF9AA6EC, 0xFFB6A99B, 0xFF7CC896, 0xFFAEA398
).map(::Color)

fun lightKaspiColors(accent: Color): KaspiColors = KaspiColors(
    dark = false,
    bg = Color(0xFFF4EEE4),
    surf = Color(0xFFFFFBF5),
    surf2 = Color(0xFFEBE3D6),
    line = Color(0xFFDCD1C1),
    ink = Color(0xFF2B2420),
    ink2 = Color(0xFF5E534A),
    ink3 = Color(0xFF776A5E),
    press = Color(0x122B2420),
    scrim = Color(0x731E1610),
    pos = Color(0xFF2D7A4B),
    neg = Color(0xFFB23E26),
    warn = Color(0xFF94560B),
    onAcc = Color(0xFFFFF8F0),
    her = PersonTone.PLUM.light,
    us = UsLight,
    accent = accent,
    categories = CategoriesLight
)

fun darkKaspiColors(accent: Color): KaspiColors = KaspiColors(
    dark = true,
    bg = Color(0xFF17130F),
    surf = Color(0xFF221C17),
    surf2 = Color(0xFF2E2620),
    line = Color(0xFF3D332B),
    ink = Color(0xFFF3EADF),
    ink2 = Color(0xFFC2B4A4),
    ink3 = Color(0xFFA09284),
    press = Color(0x14F3EADF),
    scrim = Color(0x9E000000),
    pos = Color(0xFF7CC896),
    neg = Color(0xFFF08A72),
    warn = Color(0xFFEDA65C),
    onAcc = Color(0xFF1E130C),
    her = PersonTone.PLUM.dark,
    us = UsDark,
    accent = accent,
    categories = CategoriesDark
)

fun usColor(dark: Boolean): Color = if (dark) UsDark else UsLight

val LocalKaspiColors = staticCompositionLocalOf { lightKaspiColors(PersonTone.CLAY.light) }

/** Shorthand: `Kaspi.colors.ink`, `Kaspi.type.amountM`. */
object Kaspi {
    val colors: KaspiColors
        @Composable @ReadOnlyComposable get() = LocalKaspiColors.current
    val type: KaspiType
        @Composable @ReadOnlyComposable get() = LocalKaspiType.current
}
