package com.keno.rxplayer.audio

import com.keno.rxplayer.*
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OnlinePlaybackStateTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "Sample", "Creator", null, 90)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "First", 40), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "Second", 50), 2)

    @Test fun enqueueKeepsDistinctPartsAndDoesNotResolveOrInterrupt() = runTest {
        val engine = StreamingMemoryPlayer()
        var resolved = 0
        val state = PlaybackState(engine, resolveStream = { resolved++; stream() }, initialQueue = emptyList())
        state.select(first)
        state.enqueue(second, nextInQueue = true)
        state.enqueue(second)
        assertEquals(first, state.track)
        assertTrue(state.isPlaying)
        assertEquals(listOf(first, second), state.queue)
        assertEquals(1, resolved)
    }

    @Test fun expiredPausedTrackGetsFreshInputAndPreservesPosition() = runTest {
        val engine = StreamingMemoryPlayer()
        var resolved = 0
        val state = PlaybackState(engine, resolveStream = { resolved++; stream() }, initialQueue = emptyList())
        state.select(first)
        state.seek(3000)
        state.pause()
        engine.failure = PlaybackFailure(PlaybackPhase.Expired)
        state.sync()
        state.activate()
        assertEquals(2, resolved)
        assertEquals(3000, state.positionMs)
        assertEquals(PlaybackPhase.Paused, state.phase)
        assertEquals(listOf(first), state.queue)
    }

    @Test fun nextActionMovesExistingPartWithoutDuplicatingIt() = runTest {
        val third = BilibiliTrack(item, VideoPart(3, 103, "Third", 50), 3)
        val state = PlaybackState(StreamingMemoryPlayer(), resolveStream = { stream() }, initialQueue = listOf(first, second, third))
        state.select(first)
        state.enqueue(third, nextInQueue = true)
        assertEquals(listOf(first, third, second), state.queue)
        state.toggleShuffle()
        state.enqueue(second, nextInQueue = true)
        state.step(1)
        assertEquals(second.id, state.track?.id)
    }

    @Test fun automaticExpiryRecoveryIsBoundedToOneAttempt() = runTest {
        val engine = StreamingMemoryPlayer()
        var resolved = 0
        val state = PlaybackState(engine, resolveStream = { resolved++; stream() }, initialQueue = emptyList())
        state.select(first)
        repeat(2) {
            engine.failure = PlaybackFailure(PlaybackPhase.Expired)
            state.sync()
            state.recoverExpiredIfPlaying()
        }
        assertEquals(2, resolved)
        assertEquals(PlaybackPhase.Expired, state.phase)
    }

    private fun stream() = AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "mp4a.40.2", null)
}

private class StreamingMemoryPlayer : AudioPlayer {
    override var durationMs = 10000
    override var positionMs = 0
    override var isPlaying = false
    override var failure: PlaybackFailure? = null
    override suspend fun load(bytes: ByteArray) = error("Online playback must not read bundled bytes")
    override suspend fun load(stream: AudioStream) { positionMs = 0; failure = null }
    override fun play() { isPlaying = true }
    override fun pause() { isPlaying = false }
    override fun seekTo(positionMs: Int) { this.positionMs = positionMs }
    override fun setVolume(volume: Float) = Unit
    override fun release() = Unit
}
