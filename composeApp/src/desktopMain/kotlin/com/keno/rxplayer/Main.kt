package com.keno.rxplayer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RxPlayer",
        state = rememberWindowState(width = 1200.dp, height = 820.dp),
    ) {
        window.minimumSize = Dimension(360, 600)
        App(isDesktop = true)
    }
}
