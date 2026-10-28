package com.keno.rxplayer.audio

import androidx.compose.runtime.Composable

// The prototype only plays bundled PCM WAV. Online streams belong to a later milestone.
interface AudioPlayer {
    val durationMs: Int
    val positionMs: Int
    val isPlaying: Boolean
    suspend fun load(bytes: ByteArray)
    fun play()
    fun pause()
    fun seekTo(positionMs: Int)
    fun release()
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
