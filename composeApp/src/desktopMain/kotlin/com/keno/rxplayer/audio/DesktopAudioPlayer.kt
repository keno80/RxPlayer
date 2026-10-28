package com.keno.rxplayer.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private class DesktopAudioPlayer : AudioPlayer {
    private var clip: Clip? = null
    override val durationMs: Int get() = ((clip?.microsecondLength ?: 0) / 1000).toInt()
    override val positionMs: Int get() = ((clip?.microsecondPosition ?: 0) / 1000).toInt()
    override val isPlaying: Boolean get() = clip?.isRunning == true

    override suspend fun load(bytes: ByteArray) {
        release()
        val next = AudioSystem.getClip()
        try {
            withContext(Dispatchers.IO) {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { next.open(it) }
            }
            clip = next
        } catch (failure: Exception) {
            next.close()
            throw failure
        }
    }

    override fun play() { clip?.start() }
    override fun pause() { clip?.stop() }
    override fun seekTo(positionMs: Int) { clip?.microsecondPosition = positionMs.toLong() * 1000 }
    override fun release() {
        clip?.stop()
        clip?.close()
        clip = null
    }
}
