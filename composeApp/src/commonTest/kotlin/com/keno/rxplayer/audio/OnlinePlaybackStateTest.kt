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

    @Test fun restoringDoesNotResolveOrTouchTheEngineUntilUserPlays() = runTest {
        var resolutions = 0
        var loads = 0
        val engine = object : AudioPlayer by StreamingMemoryPlayer() {
            override suspend fun load(stream: AudioStream) { loads++ }
        }
        val state = PlaybackState(engine, resolveStream = { resolutions++; stream() }, initialQueue = emptyList(),
            restored = PlaybackCheckpoint(listOf(first, second), second.id, 3200, shuffle = true, repeat = true, volume = .3f))
        assertEquals(listOf(first, second), state.queue)
        assertEquals(second, state.track)
        assertEquals(3200, state.positionMs)
        assertEquals(PlaybackPhase.Paused, state.phase)
        assertFalse(state.playWhenReady)
        assertFalse(state.canSeek)
        assertTrue(state.shuffle)
        assertTrue(state.repeat)
        assertEquals(.3f, state.volume)
        assertEquals(0, resolutions)
        assertEquals(0, loads)
        state.sync()
        assertEquals(3200, state.positionMs)
        state.activate()
        assertEquals(1, resolutions)
        assertEquals(1, loads)
        assertEquals(3200, state.positionMs)
        assertTrue(state.isPlaying)
    }

    @Test fun restoreDropsMissingSelectionAndClampsPositionToMetadata() = runTest {
        val state = PlaybackState(StreamingMemoryPlayer(), initialQueue = emptyList(),
            restored = PlaybackCheckpoint(listOf(first), first.id, Int.MAX_VALUE))
        assertEquals(first.durationMs, state.positionMs)
        val removed = PlaybackState(StreamingMemoryPlayer(), initialQueue = emptyList(),
            restored = PlaybackCheckpoint(listOf(first), second.id, 2000))
        assertNull(removed.track)
        assertEquals(0, removed.positionMs)
        assertEquals(PlaybackPhase.Idle, removed.phase)
    }

    @Test fun finishedRestoredTrackRestartsAtZeroWhenUserPlays() = runTest {
        val engine = StreamingMemoryPlayer()
        val state = PlaybackState(engine, resolveStream = { stream() }, initialQueue = emptyList(),
            restored = PlaybackCheckpoint(listOf(first), first.id, first.durationMs))
        assertFalse(state.isPlaying)
        state.activate()
        assertEquals(0, state.positionMs)
        assertTrue(state.isPlaying)
    }

    @Test fun manualSeekIsExactWhileNormalPlaybackUsesPeriodicCheckpoints() = runTest {
        val engine = StreamingMemoryPlayer()
        val state = PlaybackState(engine, resolveStream = { stream() }, initialQueue = emptyList())
        state.select(first)
        engine.positionMs = 8100
        state.sync()
        assertTrue(state.checkpoint(periodic = true).positionMs >= 5000)
        state.seek(8900)
        assertEquals(8900, state.checkpoint(periodic = true).positionMs)
        engine.positionMs = 9100
        state.sync()
        assertEquals(8900, state.checkpoint(periodic = true).positionMs)
    }

    @Test fun pauseSamplesLatestEnginePositionForFinalSave() = runTest {
        val engine = StreamingMemoryPlayer()
        val state = PlaybackState(engine, resolveStream = { stream() }, initialQueue = emptyList())
        state.select(first)
        engine.positionMs = 8450
        state.pause()
        assertEquals(8450, state.checkpoint().positionMs)
    }

    @Test fun seekingRecordsRequestedPositionBeforeAsyncEngineUpdates() = runTest {
        val delegate = StreamingMemoryPlayer()
        val engine = object : AudioPlayer by delegate {
            override fun seekTo(positionMs: Int) = Unit
        }
        val state = PlaybackState(engine, resolveStream = { stream() }, initialQueue = emptyList())
        state.select(first)
        state.seek(8900)
        assertEquals(8900, state.checkpoint(periodic = true).positionMs)
    }
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
