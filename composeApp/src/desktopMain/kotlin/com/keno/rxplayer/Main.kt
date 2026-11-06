package com.keno.rxplayer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.*
import com.keno.rxplayer.audio.dispatchWindowsMediaCommand
import com.keno.rxplayer.audio.rememberDesktopPlaybackRuntime
import kotlinx.coroutines.launch
import java.awt.Dimension

fun main() = application {
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var restoreRequest by remember { mutableIntStateOf(0) }
    var shutdown by remember { mutableStateOf<suspend () -> Unit>({}) }
    val runtime = rememberDesktopPlaybackRuntime { shutdown = it }
    val currentRuntime by rememberUpdatedState(runtime)
    var tray by remember { mutableStateOf<DesktopTray?>(null) }
    val windowState = rememberWindowState(width = 1200.dp, height = 820.dp)
    fun restore() {
        if (!closing) { visible = true; restoreRequest++ }
    }
    fun quit() {
        if (closing) return
        closing = true
        runtime?.playback?.pause()
        scope.launch { try { shutdown() } finally { exitApplication() } }
    }
    val currentQuit by rememberUpdatedState(::quit)
    DisposableEffect(Unit) {
        val installed = DesktopTray.install(
            onOpen = { restore() },
            onPlay = { if (!closing) currentRuntime?.toggle() },
            onPrevious = { if (!closing) currentRuntime?.let { dispatchWindowsMediaCommand(it, 5) } },
            onNext = { if (!closing) currentRuntime?.let { dispatchWindowsMediaCommand(it, 4) } },
            onQuit = { currentQuit() },
        )
        tray = installed
        onDispose { installed?.close() }
    }
    val playback = runtime?.playback
    val trayState = desktopTrayState((playback?.track ?: playback?.queue?.firstOrNull())?.title.orEmpty(),
        playback?.isPlaying == true, playback?.isWaiting == true, playback?.playWhenReady == true,
        playback?.track != null, playback?.queue?.size ?: 0, closing)
    SideEffect { tray?.update(trayState, closing) }
    Window(
        onCloseRequest = {
            if (!closing) { if (tray != null) visible = false else quit() }
        },
        title = "RxPlayer",
        visible = visible,
        state = windowState,
    ) {
        window.minimumSize = Dimension(360, 600)
        LaunchedEffect(restoreRequest) {
            if (visible) { windowState.isMinimized = false; window.toFront(); window.requestFocus() }
        }
        App(isDesktop = true, runtime = runtime)
    }
}
