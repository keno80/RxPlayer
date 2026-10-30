package com.keno.rxplayer.audio

import androidx.compose.runtime.Composable
import com.keno.rxplayer.source.AudioStream

interface AudioPlayer {
    val durationMs: Int
    val positionMs: Int
    val isPlaying: Boolean
    val isBuffering: Boolean get() = false
    val failure: PlaybackFailure? get() = null
    val isEnded: Boolean get() = durationMs > 0 && positionMs >= durationMs
    val supportsVolume: Boolean get() = true
    suspend fun load(bytes: ByteArray)
    suspend fun load(stream: AudioStream) { error("HTTP streams are not supported by this engine") }
    fun play()
    fun pause()
    fun seekTo(positionMs: Int)
    fun setVolume(volume: Float)
    fun release()
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
