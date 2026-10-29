// Generated from prototype-v3/cover-palettes.js; only bundled demo images.
package com.keno.rxplayer.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal fun coverColors(base: ColorScheme, dark: Boolean, artwork: Int): ColorScheme = when (artwork) {
    0 -> if (dark) base.copy(primary = Color(0xFFB8C4FF), onPrimary = Color(0xFF202C61), primaryContainer = Color(0xFF374379), onPrimaryContainer = Color(0xFFDDE1FF), secondaryContainer = Color(0xFF424659), onSecondaryContainer = Color(0xFFDFE1F9), tertiaryContainer = Color(0xFF5C3D56), onTertiaryContainer = Color(0xFFFFD7F3)) else base.copy(primary = Color(0xFF4F5B92), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF374379), secondaryContainer = Color(0xFFDFE1F9), onSecondaryContainer = Color(0xFF424659), tertiaryContainer = Color(0xFFFFD7F3), onTertiaryContainer = Color(0xFF5C3D56))
    1 -> if (dark) base.copy(primary = Color(0xFF80D5CF), onPrimary = Color(0xFF003735), primaryContainer = Color(0xFF00504D), onPrimaryContainer = Color(0xFF9CF1EB), secondaryContainer = Color(0xFF324B49), onSecondaryContainer = Color(0xFFCCE8E5), tertiaryContainer = Color(0xFF314962), onTertiaryContainer = Color(0xFFD0E4FF)) else base.copy(primary = Color(0xFF006A66), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF9CF1EB), onPrimaryContainer = Color(0xFF00504D), secondaryContainer = Color(0xFFCCE8E5), onSecondaryContainer = Color(0xFF324B49), tertiaryContainer = Color(0xFFD0E4FF), onTertiaryContainer = Color(0xFF314962))
    2 -> if (dark) base.copy(primary = Color(0xFFFFB59E), onPrimary = Color(0xFF561F0D), primaryContainer = Color(0xFF723521), onPrimaryContainer = Color(0xFFFFDBD0), secondaryContainer = Color(0xFF5D4037), onSecondaryContainer = Color(0xFFFFDBD0), tertiaryContainer = Color(0xFF52461A), onTertiaryContainer = Color(0xFFF4E2A7)) else base.copy(primary = Color(0xFF8F4C36), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFFFDBD0), onPrimaryContainer = Color(0xFF723521), secondaryContainer = Color(0xFFFFDBD0), onSecondaryContainer = Color(0xFF5D4037), tertiaryContainer = Color(0xFFF4E2A7), onTertiaryContainer = Color(0xFF52461A))
    else -> base
}
