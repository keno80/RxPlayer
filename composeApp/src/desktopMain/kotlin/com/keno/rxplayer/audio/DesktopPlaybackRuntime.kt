package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.persistence.rememberLibraryStore
import com.keno.rxplayer.source.BilibiliSource
import kotlinx.coroutines.*

@Composable
actual fun rememberPlaybackRuntime(): PlaybackRuntime? {
    val store = rememberLibraryStore()
    val runtime by produceState<PlaybackRuntime?>(null, store) {
        val engine = DesktopAudioPlayer()
        val source = BilibiliSource()
        var owned: PlaybackRuntime? = null
        try {
            owned = openPlaybackRuntime(engine, store, { source.resolve(it.item.id, it.part.cid) }, { engine.release(); source.close() })
            value = owned
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { if (owned != null) owned.close() else { engine.release(); source.close() } }
        }
    }
    return runtime
}
