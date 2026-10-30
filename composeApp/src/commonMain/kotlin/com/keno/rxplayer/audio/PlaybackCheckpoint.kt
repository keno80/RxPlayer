package com.keno.rxplayer.audio

import com.keno.rxplayer.PlaybackTrack

/** Content identity and user intent only; never contains a resolved stream. */
data class PlaybackCheckpoint(
    val queue: List<PlaybackTrack> = emptyList(),
    val currentId: String? = null,
    val positionMs: Int = 0,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val volume: Float = .65f,
)
