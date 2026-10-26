package com.kleinapps.kaspitracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Radii from the design: bar 8, icon/chip 12, field/tile 16, card 20, hero/sheet 28. */
object Radius {
    val bar = 8.dp
    val icon = 12.dp
    val field = 16.dp
    val card = 20.dp
    val hero = 28.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.bar),
    small = RoundedCornerShape(Radius.icon),
    medium = RoundedCornerShape(Radius.field),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.hero)
)

/** Material roles filled from the warm palette, so stock components fit in. */
private fun schemeOf(c: KaspiColors): ColorScheme {
    val base = if (c.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAcc,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.ink,
        secondary = c.accent,
        onSecondary = c.onAcc,
        secondaryContainer = c.accentSoft,
        onSecondaryContainer = c.ink,
        tertiary = c.us,
        background = c.bg,
        onBackground = c.ink,
        surface = c.surf,
        onSurface = c.ink,
        surfaceVariant = c.surf2,
        onSurfaceVariant = c.ink2,
        surfaceTint = Color.Transparent,
        surfaceBright = c.surf,
        surfaceDim = c.bg,
        surfaceContainerLowest = c.surf,
        surfaceContainerLow = c.surf,
        surfaceContainer = c.surf,
        surfaceContainerHigh = c.surf,
        surfaceContainerHighest = c.surf2,
        inverseSurface = c.ink,
        inverseOnSurface = c.bg,
        outline = c.line,
        outlineVariant = c.line,
        error = c.neg,
        onError = c.onAcc,
        scrim = c.scrim
    )
}

@Composable
fun KaspiTrackerTheme(content: @Composable () -> Unit) {
    val mode by ThemeController.mode.collectAsStateWithLifecycle()
    val tone by ThemeController.tone.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = tone.color(dark)
    val colors = remember(dark, accent) {
        if (dark) darkKaspiColors(accent) else lightKaspiColors(accent)
    }
    val type = remember { KaspiType() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalKaspiColors provides colors, LocalKaspiType provides type) {
        MaterialTheme(
            colorScheme = schemeOf(colors),
            typography = materialTypography(type),
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Re-colours a subtree with another accent — the partner's colour on her
 * view, gold on "together". Everything below, stock Material parts included,
 * picks it up.
 */
@Composable
fun ProvideAccent(accent: Color, content: @Composable () -> Unit) {
    val colors = LocalKaspiColors.current.copy(accent = accent)
    CompositionLocalProvider(LocalKaspiColors provides colors) {
        MaterialTheme(
            colorScheme = schemeOf(colors),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
