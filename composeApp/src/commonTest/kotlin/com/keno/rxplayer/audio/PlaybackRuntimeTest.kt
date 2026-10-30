package com.keno.rxplayer.audio

import com.keno.rxplayer.*
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackRuntimeTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频", "作者", null, 20)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "P1", 10), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "P2", 10), 2)

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
}

private class RuntimeEngine : AudioPlayer {
    override val durationMs = 10000
    override var positionMs = 0
    override var isPlaying = false
    override suspend fun load(bytes: ByteArray) { positionMs = 0 }
    override suspend fun load(stream: AudioStream) { positionMs = 0 }
    override fun play() { isPlaying = true }
    override fun pause() { isPlaying = false }
    override fun seekTo(positionMs: Int) { this.positionMs = positionMs }
    override fun setVolume(volume: Float) = Unit
    override fun release() { isPlaying = false }
}
