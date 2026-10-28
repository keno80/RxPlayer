@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.keno.rxplayer.ui

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun RxTheme(dark: Boolean, artwork: Int, content: @Composable () -> Unit) {
    val accent = when (artwork) {
        1 -> Color(0xFFFFD6A0)
        2 -> Color(0xFFD9C7FF)
        else -> Color(0xFFD5ED9B)
    }
    val ink = when (artwork) {
        1 -> Color(0xFF78562C)
        2 -> Color(0xFF68548D)
        else -> Color(0xFF4A622B)
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = Color(0xFF20251A),
            primaryContainer = ink, onPrimaryContainer = accent,
            secondaryContainer = Color(0xFF34392F), onSecondaryContainer = Color(0xFFE3E8DB),
            surface = Color(0xFF151713), onSurface = Color(0xFFE7E9DF),
            surfaceContainer = Color(0xFF20231D),
            surfaceContainerLow = Color(0xFF1B1E18),
            surfaceContainerHigh = Color(0xFF2B2F25),
            surfaceVariant = Color(0xFF34392F), onSurfaceVariant = Color(0xFFBCC3B2),
            outline = Color(0xFF8B9380), outlineVariant = Color(0xFF41483B),
        )
    } else {
        lightColorScheme(
            primary = ink, onPrimary = Color.White,
            primaryContainer = accent, onPrimaryContainer = Color(0xFF29331E),
            secondaryContainer = Color(0xFFE6EBD9), onSecondaryContainer = Color(0xFF3D4734),
            surface = Color(0xFFF8F9F2), onSurface = Color(0xFF20251D),
            surfaceContainer = Color(0xFFEEF0E6),
            surfaceContainerLow = Color(0xFFF2F4EC),
            surfaceContainerHigh = Color(0xFFE6E9DD),
            surfaceVariant = Color(0xFFE1E6D7), onSurfaceVariant = Color(0xFF626A58),
            outline = Color(0xFF747D68), outlineVariant = Color(0xFFD3D9C8),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
