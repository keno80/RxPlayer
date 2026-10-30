@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.keno.rxplayer.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.DemoTrack
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

// Brand roles match the approved prototype's MCU-generated colors.css.
internal fun brandColors(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF81D5CB), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9DF2E7),
    secondary = Color(0xFFB1CCC7), onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF324B48), onSecondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFFFFB874), onTertiary = Color(0xFF4B2800),
    tertiaryContainer = Color(0xFF6A3B00), onTertiaryContainer = Color(0xFFFFDCBF),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceContainerLowest = Color(0xFF090F0E), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899391), outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    inverseSurface = Color(0xFFDDE4E2), inverseOnSurface = Color(0xFF2B3230), inversePrimary = Color(0xFF006A62),
) else lightColorScheme(
    primary = Color(0xFF006A62), onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2E7), onPrimaryContainer = Color(0xFF00504A),
    secondary = Color(0xFF4A6360), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E3), onSecondaryContainer = Color(0xFF324B48),
    tertiary = Color(0xFF8C5000), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBF), onTertiaryContainer = Color(0xFF6A3B00),
    surface = Color(0xFFF4FBF8), onSurface = Color(0xFF161D1C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED), surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E2), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFBA1A1A), onError = Color.White,
    inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFEFF5F3), inversePrimary = Color(0xFF81D5CB),
)

@Composable
fun RxTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlayerReducedMotion provides rememberPlayerReducedMotion()) {
        MaterialExpressiveTheme(colorScheme = brandColors(dark), motionScheme = MotionScheme.expressive(), content = content)
    }
}

@Composable
fun PlayerTheme(dark: Boolean, artwork: Int?, content: @Composable () -> Unit) {
    val base = brandColors(dark)
    MaterialExpressiveTheme(
        colorScheme = artwork?.let { coverColors(base, dark, it) } ?: base,
        motionScheme = MotionScheme.expressive(), content = content,
    )
}

@Composable
fun PlayerTheme(dark: Boolean, track: PlaybackTrack?, followArtwork: Boolean = true, content: @Composable () -> Unit) {
    val store = LocalArtworkStore.current
    LaunchedEffect(track?.artworkUrl, followArtwork) { if (followArtwork) store.load(track?.artworkUrl) }
    val seed = if (followArtwork) store.get(track?.artworkUrl)?.seed else null
    val colors = androidx.compose.runtime.remember(dark, seed, followArtwork, track?.artwork) {
        val base = brandColors(dark)
        when {
            seed != null -> SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0).let { s -> base.copy(
                primary = Color(s.primary), onPrimary = Color(s.onPrimary), primaryContainer = Color(s.primaryContainer), onPrimaryContainer = Color(s.onPrimaryContainer),
                secondary = Color(s.secondary), onSecondary = Color(s.onSecondary), secondaryContainer = Color(s.secondaryContainer), onSecondaryContainer = Color(s.onSecondaryContainer),
                tertiary = Color(s.tertiary), onTertiary = Color(s.onTertiary), tertiaryContainer = Color(s.tertiaryContainer), onTertiaryContainer = Color(s.onTertiaryContainer),
            ) }
            followArtwork && track is DemoTrack -> coverColors(base, dark, track.artwork)
            else -> base
        }
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
