package com.keno.rxplayer.audio

import com.keno.rxplayer.*
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class WindowsMediaSessionTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频", "作者", null, 20)
    private val first = BilibiliTrack(item, VideoPart(1, 1, "P1", 10), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 2, "P2", 10), 2)

    @Test fun metadataAndCommandsShareTheActualPlaybackSession() = runTest {
        val engine = MediaTestEngine()
        val state = PlaybackState(engine, resolveStream = { AudioStream("https://sample.bilivideo.com/audio", emptyMap(), "audio/mp4", "aac", null) }, initialQueue = listOf(first, second))
        val runtime = PlaybackRuntime(state, LibrarySnapshot(), null, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            dispatchWindowsMediaCommand(runtime, 1)?.join()
            assertTrue(state.isPlaying)
            dispatchWindowsMediaCommand(runtime, 1)?.join()
            assertTrue(state.isPlaying, "A stale Play command must not pause playback")
            val playing = windowsMediaState(state)
            assertEquals(first.title, playing.title)
            assertEquals(item.author, playing.artist)
            assertTrue(playing.enabled)
            assertTrue(playing.canSkip)
            assertEquals(3, playing.status)
            dispatchWindowsMediaCommand(runtime, 2)?.join()
            assertFalse(state.playWhenReady)
            dispatchWindowsMediaCommand(runtime, 2)?.join()
            assertFalse(state.playWhenReady, "A stale Pause command must not resume playback")
            assertEquals(4, windowsMediaState(state).status)
            dispatchWindowsMediaCommand(runtime, 4)?.join()
            assertEquals(second.id, state.track?.id)
            assertFalse(state.playWhenReady)
            dispatchWindowsMediaCommand(runtime, 5)?.join()
            assertEquals(first.id, state.track?.id)
            dispatchWindowsMediaCommand(runtime, 3)?.join()
            assertFalse(state.isPlaying)
            assertNull(dispatchWindowsMediaCommand(runtime, 999))
        } finally { runtime.close() }
    }

    @Test fun emptySessionDoesNotAdvertiseControls() {
        val state = windowsMediaState(PlaybackState(MediaTestEngine(), initialQueue = emptyList()))
        assertFalse(state.enabled)
        assertFalse(state.canSkip)
        assertEquals("", state.title)
    }
}

private class MediaTestEngine : AudioPlayer {
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
