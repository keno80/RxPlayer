package com.keno.rxplayer.ui

import androidx.compose.runtime.*
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Desktop Escape is handled in the existing Compose window's key event chain.
@Composable
actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
internal actual fun rememberPlayerReducedMotion(): Boolean {
    val settings = remember { Native.load("user32", WindowsAnimationSettings::class.java) }
    fun read(): Boolean {
        val enabled = IntByReference(1)
        // SPI_GETCLIENTAREAANIMATION, a read-only Windows accessibility query.
        return settings.SystemParametersInfoW(0x1042, 0, enabled, 0) && enabled.value == 0
    }
    var reduced by remember(settings) { mutableStateOf(read()) }
    LaunchedEffect(settings) {
        while (isActive) {
            delay(1_000)
            reduced = read()
        }
    }
    return reduced
}

internal interface WindowsAnimationSettings : StdCallLibrary {
    fun SystemParametersInfoW(action: Int, parameter: Int, value: IntByReference, flags: Int): Boolean
}
