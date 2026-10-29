package com.keno.rxplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

@Composable
expect fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit)

internal val LocalPlayerReducedMotion = staticCompositionLocalOf { false }

@Composable
internal expect fun rememberPlayerReducedMotion(): Boolean
