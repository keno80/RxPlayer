package com.keno.rxplayer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import java.awt.Dimension

fun main() = application {
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var flush by remember { mutableStateOf<suspend () -> Unit>({}) }
    Window(
        onCloseRequest = {
            if (!closing) {
                closing = true
                scope.launch { try { flush() } finally { exitApplication() } }
            }
        },
        title = "RxPlayer",
        state = rememberWindowState(width = 1200.dp, height = 820.dp),
    ) {
        window.minimumSize = Dimension(360, 600)
        App(isDesktop = true, onPersistenceReady = { flush = it })
    }
}
