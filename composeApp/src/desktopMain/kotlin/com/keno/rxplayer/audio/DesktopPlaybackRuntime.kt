package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.persistence.rememberLibraryStore
import com.keno.rxplayer.source.BilibiliSource
import kotlinx.coroutines.*

@Composable
actual fun rememberPlaybackRuntime(): PlaybackRuntime? = rememberDesktopPlaybackRuntime()

@Composable
internal fun rememberDesktopPlaybackRuntime(onShutdownReady: (suspend () -> Unit) -> Unit = {}): PlaybackRuntime? {
    val store = rememberLibraryStore()
    val shutdownReady by rememberUpdatedState(onShutdownReady)
    val runtime by produceState<PlaybackRuntime?>(null, store) {
        val owner = currentCoroutineContext().job
        shutdownReady { owner.cancelAndJoin() }
        val engine = DesktopAudioPlayer()
        val source = BilibiliSource()
        var owned: PlaybackRuntime? = null
        var media: Job? = null
        try {
            owned = openPlaybackRuntime(engine, store, { source.resolve(it.item.id, it.part.cid) }, { engine.release(); source.close() })
            value = owned
            media = launch { runWindowsMediaSession(owned) }
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { media?.cancelAndJoin(); if (owned != null) owned.close() else { engine.release(); source.close() } }
        }
    }
    return runtime
}
