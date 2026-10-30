package com.keno.rxplayer.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import org.junit.Assume.assumeTrue
import kotlin.test.*

class DesktopSeekIntentTest {
    @Test fun pendingSeekPublishesTargetWithoutWaitingForIpc() {
        val player = DesktopAudioPlayer()
        try {
            player.seekTo(14900)
            assertEquals(14900, player.positionMs)
            assertFalse(player.isEnded)
            player.seekTo(3200)
            assertEquals(3200, player.positionMs)
        } finally { player.release() }
    }

    @Test fun realMpvConfirmsSeekWithoutStartingPlayback() = runBlocking {
        val audio = System.getenv("RXPLAYER_SEEK_AUDIO_PATH")
        assumeTrue(audio != null && System.getenv("RXPLAYER_MPV_PATH") != null)
        val player = DesktopAudioPlayer()
        try {
            player.load(File(audio!!).readBytes())
            player.seekTo(8900)
            assertEquals(8900, player.positionMs)
            withTimeout(10000) { while (player.isBuffering) { assertNull(player.failure); delay(20) } }
            assertTrue(kotlin.math.abs(player.positionMs - 8900) < 150)
            assertFalse(player.isPlaying)
            player.seekTo(7000)
            player.seekTo(3200)
            withTimeout(10000) { while (player.isBuffering) { assertNull(player.failure); delay(20) } }
            assertTrue(kotlin.math.abs(player.positionMs - 3200) < 150)
            assertFalse(player.isPlaying)
        } finally { player.release() }
    }
}
