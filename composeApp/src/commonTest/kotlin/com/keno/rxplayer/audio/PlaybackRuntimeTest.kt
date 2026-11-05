package com.keno.rxplayer.audio

import com.keno.rxplayer.*
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.library.LocalPlaylist
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackRuntimeTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频", "作者", null, 20)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "P1", 10), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "P2", 10), 2)

    @Test fun playingPlaylistCancelsOldResolutionWithoutRestoringOldQueue() = runTest {
        val pending = CompletableDeferred<AudioStream>()
        val engine = RuntimeEngine()
        val newStream = AudioStream("https://sample.bilivideo.com/new", emptyMap(), "audio/mp4", "aac", null)
        val state = PlaybackState(engine, resolveStream = {
            if (it.id == second.id) withContext(NonCancellable) { pending.await() } else newStream
        }, initialQueue = listOf(second))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(playlists = listOf(LocalPlaylist("list", "通勤", listOf(first)))), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            val oldSelection = runtime.select(second)
            assertEquals(PlaybackPhase.Preparing, state.phase)
            val replacement = runtime.playPlaylist("list")!!
            pending.complete(AudioStream("https://sample.bilivideo.com/old", emptyMap(), "audio/mp4", "aac", null))
            oldSelection.join(); replacement.join()
            assertEquals(listOf(first), state.queue)
            assertEquals(first.id, state.track?.id)
            assertEquals(listOf(newStream), engine.loadedStreams)
            assertTrue(state.isPlaying)
        } finally { runtime.close() }
    }

    @Test fun playlistPlaybackUsesSnapshotAndAppendingKeepsCurrentPlayback() = runTest {
        val engine = RuntimeEngine()
        val state = PlaybackState(engine, resolveStream = { AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null) }, initialQueue = listOf(second))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(playlists = listOf(LocalPlaylist("list", "通勤", listOf(first, second)), LocalPlaylist("empty", "空白"))), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            runtime.select(second).join()
            state.seek(3000)
            runtime.appendPlaylist("list")
            assertEquals(listOf(second, first), state.queue)
            assertEquals(second.id, state.track?.id)
            assertEquals(3000, state.positionMs)
            assertTrue(state.isPlaying)
            runtime.playPlaylist("empty")?.join()
            assertEquals(second.id, state.track?.id)
            state.toggleShuffle()
            runtime.playPlaylist("list")!!.join()
            assertEquals(listOf(first, second), state.queue)
            assertEquals(first.id, state.track?.id)
            assertFalse(state.shuffle)
            assertTrue(state.isPlaying)
            runtime.playlists.remove("list", first.id)
            assertEquals(listOf(first, second), state.queue)
            runtime.playlists.delete("list")
            assertEquals(first.id, state.track?.id)
        } finally { runtime.close() }
    }

    @Test fun advancesAndRecordsHistoryWithoutAnyAttachedScreen() = runTest {
        val engine = RuntimeEngine()
        val state = PlaybackState(engine, resolveStream = { AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null) }, initialQueue = listOf(first, second))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            runtime.select(first).join()
            runCurrent()
            engine.positionMs = engine.durationMs
            advanceTimeBy(200); runCurrent()
            assertEquals(second.id, state.track?.id)
            assertTrue(state.isPlaying)
            assertEquals(second.id, runtime.recent.firstOrNull()?.id)
        } finally { runtime.close() }
    }

    @Test fun screenFlushDoesNotStopAudioButFinalClosePausesAndDisposes() = runTest {
        val engine = RuntimeEngine()
        var released = false
        val state = PlaybackState(engine, resolveStream = { AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null) }, initialQueue = listOf(first))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, { released = true }, StandardTestDispatcher(testScheduler))
        runtime.select(first).join()
        runtime.flush()
        assertTrue(engine.isPlaying)
        assertFalse(released)
        runtime.close()
        assertFalse(engine.isPlaying)
        assertTrue(released)
    }

    @Test fun systemSelectionKeepsPauseIntentAndHonorsExactIndexAndPosition() = runTest {
        val engine = RuntimeEngine()
        val state = PlaybackState(engine, resolveStream = { AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null) }, initialQueue = listOf(first, second))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            runtime.select(first).join()
            state.pause()
            runtime.seekToQueueItem(1, 3200)?.join()
            assertEquals(second.id, state.track?.id)
            assertEquals(3200, state.positionMs)
            assertFalse(state.playWhenReady)
            assertFalse(engine.isPlaying)
            runtime.seekToQueueItem(1, 0)?.join()
            assertEquals(second.id, state.track?.id)
            assertEquals(0, state.positionMs)
            runtime.seekToQueueItem(-1, 0)?.join()
            assertEquals(second.id, state.track?.id)
            assertFalse(engine.isPlaying)
            assertEquals(listOf(first, second), state.queue)
        } finally { runtime.close() }
    }

    @Test fun systemPauseDuringResolutionCannotStartAudioAfterwards() = runTest {
        val pending = CompletableDeferred<AudioStream>()
        val engine = RuntimeEngine()
        val state = PlaybackState(engine, resolveStream = { pending.await() }, initialQueue = listOf(first))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            runtime.select(first)
            assertEquals(PlaybackPhase.Preparing, state.phase)
            state.pause()
            pending.complete(AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null))
            runCurrent()
            assertFalse(engine.isPlaying)
            assertFalse(state.playWhenReady)
            assertEquals(PlaybackPhase.Paused, state.phase)
        } finally { runtime.close() }
    }

    @Test fun returningToSessionKeepsSearchAndContinuesFromTheNextPage() = runTest {
        val pages = mutableListOf<Int>()
        val source = BilibiliSource(HttpClient(MockEngine { request ->
            val page = request.url.parameters["page"]?.toIntOrNull()
            if (page != null) pages += page
            val id = if (page == 2) "BV1Ekub6zE3D" else "BV14u41147YH"
            respond("""{"code":0,"data":{"numPages":2,"result":[{"bvid":"$id","title":"Result $page","author":"Artist","duration":"00:10"}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        val state = PlaybackState(RuntimeEngine(), initialQueue = emptyList())
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, dispatcher = StandardTestDispatcher(testScheduler), browseSourceFactory = { source })
        try {
            val entered = runtime.browse
            entered.query = "甲乙丙丁"
            entered.submit()!!.join()
            assertEquals(1, entered.results.size)
            val returned = runtime.browse
            assertEquals("甲乙丙丁", returned.query)
            assertEquals("甲乙丙丁", returned.submitted)
            assertEquals(entered.results, returned.results)
            returned.more()!!.join()
            assertEquals(listOf(1, 2), pages)
            assertEquals(2, returned.results.size)
            assertEquals(emptyList(), state.queue)
        } finally { runtime.close() }
    }
}

private class RuntimeEngine : AudioPlayer {
    val loadedStreams = mutableListOf<AudioStream>()
    override val durationMs = 10000
    override var positionMs = 0
    override var isPlaying = false
    override suspend fun load(bytes: ByteArray) { positionMs = 0 }
    override suspend fun load(stream: AudioStream) { loadedStreams += stream; positionMs = 0 }
    override fun play() { isPlaying = true }
    override fun pause() { isPlaying = false }
    override fun seekTo(positionMs: Int) { this.positionMs = positionMs }
    override fun setVolume(volume: Float) = Unit
    override fun release() { isPlaying = false }
}
