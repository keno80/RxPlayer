package com.keno.rxplayer.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { AndroidAudioPlayer(context.cacheDir) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private class AndroidAudioPlayer(private val cacheDir: File) : AudioPlayer {
    private var player: MediaPlayer? = null
    override val durationMs: Int get() = player?.duration ?: 0
    override val positionMs: Int get() = player?.currentPosition ?: 0
    override val isPlaying: Boolean get() = player?.isPlaying == true

    override suspend fun load(bytes: ByteArray) {
        release()
        val next = MediaPlayer()
        try {
            withContext(Dispatchers.IO) {
                val file = File.createTempFile("rxplayer-", ".wav", cacheDir)
                try {
                    file.writeBytes(bytes)
                    next.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    next.setDataSource(file.absolutePath)
                    next.prepare()
                } finally {
                    file.delete()
                }
            }
            player = next
        } catch (failure: Exception) {
            // Includes cancellation between preparation and returning to the UI dispatcher.
            next.release()
            throw failure
        }
    }

    override fun play() { player?.start() }
    override fun pause() { if (isPlaying) player?.pause() }
    override fun seekTo(positionMs: Int) { player?.seekTo(positionMs.toLong(), MediaPlayer.SEEK_CLOSEST) }
    override fun release() {
        player?.release()
        player = null
    }
}
