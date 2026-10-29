package com.keno.rxplayer.audio

import androidx.compose.runtime.Composable

// The prototype only plays bundled PCM WAV. Online streams belong to a later milestone.
interface AudioPlayer {
    val durationMs: Int
    val positionMs: Int
    val isPlaying: Boolean
    val isBuffering: Boolean get() = false
    val failure: PlaybackFailure? get() = null
    val isEnded: Boolean get() = durationMs > 0 && positionMs >= durationMs
    val supportsVolume: Boolean get() = true
    suspend fun load(bytes: ByteArray)
    fun play()
    fun pause()
    fun seekTo(positionMs: Int)
    fun setVolume(volume: Float)
    fun release()
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
