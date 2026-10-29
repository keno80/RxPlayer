@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.keno.rxplayer.audio

import com.keno.rxplayer.demoTracks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PlaybackStateTest {
    @Test fun cancelledPreparationNeverPlays() = runTest {
        val engine = MemoryAudioPlayer()
        val bytes = CompletableDeferred<ByteArray>()
        val state = PlaybackState(engine) { bytes.await() }
        launch { state.select(demoTracks.first()) }
        runCurrent()
        assertEquals(PlaybackPhase.Preparing, state.phase)
        state.cancelPreparation()
        bytes.complete(byteArrayOf(1))
        advanceUntilIdle()
        assertFalse(state.isPlaying)
        assertFalse(state.isLoading)
        assertEquals(0, engine.playCount)
    }

    @Test fun latestSelectionWins() = runTest {
        val gate = CompletableDeferred<ByteArray>()
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { if (it == demoTracks[0]) gate.await() else byteArrayOf(2) }
        launch { state.select(demoTracks[0]) }
        runCurrent()
        launch { state.select(demoTracks[1]) }
        advanceUntilIdle()
        gate.complete(byteArrayOf(1))
        advanceUntilIdle()
        assertEquals(demoTracks[1], state.track)
        assertEquals(1, engine.playCount)
        assertTrue(state.isPlaying)
    }

    @Test fun queueEndStopsWithoutMessageAndExplicitPlayRestarts() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.last())
        engine.positionMs = engine.durationMs
        engine.isPlaying = false
        assertNull(state.sync())
        assertEquals(PlaybackPhase.Ended, state.phase)
        assertNull(state.message)
        assertNull(state.sync())
        state.activate()
        assertEquals(0, state.positionMs)
        assertTrue(state.isPlaying)
    }

    @Test fun engineCompletionReturnsNextOnlyOnce() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.first())
        engine.positionMs = engine.durationMs
        engine.isPlaying = false
        assertEquals(demoTracks[1], state.sync())
        assertNull(state.sync())
    }

    @Test fun refreshPreservesPausePositionAndQueue() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.first())
        state.seek(4_000)
        state.pause()
        val queue = state.queue
        engine.failure = PlaybackFailure(PlaybackPhase.Expired)
        state.sync()
        assertEquals(PlaybackPhase.Expired, state.phase)
        state.activate()
        assertEquals(PlaybackPhase.Paused, state.phase)
        assertEquals(4_000, state.positionMs)
        assertEquals(queue, state.queue)
    }

    @Test fun bufferingDoesNotAdvanceAndPauseClearsPlayIntent() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.first())
        state.seek(2_000)
        engine.isBuffering = true
        engine.isPlaying = false
        state.sync()
        assertEquals(PlaybackPhase.Buffering, state.phase)
        state.pause()
        engine.isBuffering = false
        state.sync()
        assertEquals(PlaybackPhase.Paused, state.phase)
        assertEquals(2_000, state.positionMs)
        assertFalse(state.playWhenReady)
    }

    @Test fun failureIsOneEventAndKeepsRecoveryPosition() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.first())
        state.seek(3_000)
        engine.failure = PlaybackFailure(PlaybackPhase.Failed)
        state.sync()
        assertFalse(engine.isPlaying)
        val message = state.message
        state.sync()
        assertEquals(message, state.message)
        assertEquals(3_000, state.positionMs)
        state.activate()
        assertEquals(3_000, state.positionMs)
        assertTrue(state.isPlaying)
    }

    @Test fun removingCurrentCancelsPreparation() = runTest {
        val engine = MemoryAudioPlayer()
        val gate = CompletableDeferred<ByteArray>()
        val state = PlaybackState(engine) { gate.await() }
        launch { state.select(demoTracks.first()) }
        runCurrent()
        state.removeFromQueue(demoTracks.first().id)
        gate.complete(byteArrayOf(1))
        advanceUntilIdle()
        assertEquals(0, engine.playCount)
        assertFalse(state.isPlaying)
        assertEquals(demoTracks[1], state.track)
    }

    @Test fun shuffleCompletesEveryQueueEntryOnceBeforeStopping() = runTest {
        val engine = MemoryAudioPlayer()
        val state = PlaybackState(engine) { byteArrayOf(1) }
        state.select(demoTracks.last())
        state.toggleShuffle()
        val visited = mutableSetOf(state.track!!.id)
        repeat(demoTracks.size) { index ->
            engine.positionMs = engine.durationMs
            engine.isPlaying = false
            val next = state.sync()
            if (index < demoTracks.lastIndex) {
                assertNotNull(next)
                assertTrue(visited.add(next.id))
                state.select(next)
            } else assertNull(next)
        }
        assertEquals(demoTracks.map { it.id }.toSet(), visited)
        assertEquals(PlaybackPhase.Ended, state.phase)
        assertNull(state.message)
    }
}

private class MemoryAudioPlayer : AudioPlayer {
    override var durationMs = 10_000
    override var positionMs = 0
    override var isPlaying = false
    override var isBuffering = false
    override var failure: PlaybackFailure? = null
    var playCount = 0
    override suspend fun load(bytes: ByteArray) { positionMs = 0; failure = null }
    override fun play() { playCount++; isPlaying = true }
    override fun pause() { isPlaying = false }
    override fun seekTo(positionMs: Int) { this.positionMs = positionMs }
    override fun setVolume(volume: Float) = Unit
    override fun release() = Unit
}
