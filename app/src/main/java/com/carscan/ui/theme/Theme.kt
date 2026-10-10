package com.carscan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPalette = staticCompositionLocalOf { Palettes.Cyan }

/** Приложение всегда тёмное: магнитола ночью не должна слепить. Гамма выбирается в настройках. */
@Composable
fun CarScanTheme(palette: CarScanPalette = Palettes.Cyan, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                secondary = palette.active,
                tertiary = Neon.Amber,
                error = Neon.Red,
                background = palette.background,
                surface = palette.background,
                onPrimary = palette.background,
                onBackground = Neon.Text,
                onSurface = Neon.Text,
            ),
            typography = Typography,
            content = content,
        )
    }
}

object CarScanTheme {
    val palette: CarScanPalette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current
}
