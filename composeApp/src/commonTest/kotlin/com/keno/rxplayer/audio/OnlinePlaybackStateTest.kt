@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.keno.rxplayer.audio

import com.keno.rxplayer.*
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.test.*

class OnlinePlaybackStateTest {
    @Test fun transientSourceFailuresStayRetryableAndUseTheExistingSpecificMessages() = runTest {
        for (kind in listOf(SourceFailureKind.Network, SourceFailureKind.Restricted, SourceFailureKind.RateLimited, SourceFailureKind.InvalidResponse)) {
            var fail = true
            val state = PlaybackState(StreamingMemoryPlayer(), resolveStream = { if (fail) throw SourceFailure(kind) else stream() }, initialQueue = listOf(first, second))
            state.select(first)
            assertEquals(PlaybackPhase.Failed, state.phase)
            assertEquals(friendlySourceError(SourceFailure(kind)), state.message?.text)
            assertEquals(listOf(first, second), state.queue)
            fail = false; state.activate()
            assertTrue(state.isPlaying); assertEquals(first, state.track)
        }
    }

    @Test fun permanentSourceFailuresKeepTheirReasonAndNeverClaimTheyAreNetworkErrors() = runTest {
        for (kind in listOf(SourceFailureKind.Unavailable, SourceFailureKind.LoginRequired, SourceFailureKind.UnsupportedAudio)) {
            var attempts = 0
            val state = PlaybackState(StreamingMemoryPlayer(), resolveStream = { attempts++; throw SourceFailure(kind) }, initialQueue = listOf(first, second))
            state.select(first)
            assertEquals(PlaybackPhase.Unavailable, state.phase)
            val reason = friendlySourceError(SourceFailure(kind))
            assertEquals(reason, state.message?.text)
            state.activate()
            assertEquals(1, attempts)
            assertEquals(reason, state.message?.text)
            assertEquals(listOf(first, second), state.queue)
        }
    }

    @Test fun failedAddressRefreshKeepsPausedPositionAndDoesNotSpendAnotherAutomaticAttempt() = runTest {
        val engine = StreamingMemoryPlayer()
        var attempts = 0
        var fail = false
        val state = PlaybackState(engine, resolveStream = { attempts++; if (fail) throw SourceFailure(SourceFailureKind.Network); stream() }, initialQueue = listOf(first, second))
        state.select(first); state.seek(3400); state.pause()
        engine.failure = PlaybackFailure(PlaybackPhase.Expired); state.sync()
        fail = true; state.activate()
        assertEquals(PlaybackPhase.Expired, state.phase)
        assertEquals(3400, state.positionMs); assertFalse(state.playWhenReady)
        state.recoverExpiredIfPlaying()
        assertEquals(2, attempts)
        fail = false; state.activate()
        assertEquals(PlaybackPhase.Paused, state.phase)
        assertEquals(3400, state.positionMs); assertEquals(listOf(first, second), state.queue)
        assertEquals(3, attempts)
    }

    @Test fun manualRefreshFailureCannotTriggerAnExtraAutomaticLoop() = runTest {
        val engine = StreamingMemoryPlayer()
        var attempts = 0
        val state = PlaybackState(engine, resolveStream = { attempts++; if (attempts > 1) throw SourceFailure(SourceFailureKind.Network); stream() }, initialQueue = listOf(first, second))
        state.select(first); state.seek(3100)
        engine.failure = PlaybackFailure(PlaybackPhase.Expired); state.sync()
        state.activate()
        repeat(3) { state.recoverExpiredIfPlaying() }
        assertEquals(2, attempts)
        assertEquals(PlaybackPhase.Expired, state.phase)
        assertEquals(3100, state.positionMs)
    }

    @Test fun cancelledRefreshCannotPlayOrReplaceANewerTrackWhenItsResolverReturnsLate() = runTest {
        val engine = StreamingMemoryPlayer()
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val state = PlaybackState(engine, resolveStream = { track ->
            if (track.id == first.id && ++attempts > 1) withContext(NonCancellable) { gate.await() }
            stream()
        }, initialQueue = listOf(first, second))
        state.select(first); state.seek(3300)
        engine.failure = PlaybackFailure(PlaybackPhase.Expired); state.sync()
        val refreshing = launch { state.activate() }; runCurrent()
        assertEquals(PlaybackPhase.Refreshing, state.phase)
        state.cancelPreparation()
        assertEquals(PlaybackPhase.Expired, state.phase); assertFalse(state.playWhenReady)
        val selected = launch { state.select(second, autoPlay = false) }
        runCurrent(); gate.complete(Unit); refreshing.join(); selected.join()
        assertEquals(second, state.track); assertEquals(PlaybackPhase.Paused, state.phase)
        assertFalse(engine.isPlaying); assertEquals(listOf(first, second), state.queue)
    }

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

    @Test fun systemPauseClearsUserIntentAndPreventsBackgroundAdvance() = runTest {
        val delegate = StreamingMemoryPlayer()
        var intent: Boolean? = null
        val engine = object : AudioPlayer by delegate { override val playWhenReady get() = intent }
        val state = PlaybackState(engine, resolveStream = { stream() }, initialQueue = listOf(first, second))
        state.select(first)
        delegate.pause(); intent = false
        state.sync()
        assertFalse(state.playWhenReady)
        assertEquals(PlaybackPhase.Paused, state.phase)
        delegate.positionMs = delegate.durationMs
        assertNull(state.sync())
        assertEquals(first, state.track)
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
