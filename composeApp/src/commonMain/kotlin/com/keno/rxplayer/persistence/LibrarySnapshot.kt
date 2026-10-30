package com.keno.rxplayer.persistence

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.audio.PlaybackCheckpoint
import com.keno.rxplayer.source.ContentItem

data class LibrarySnapshot(
    val favorites: List<ContentItem> = emptyList(),
    val recent: List<BilibiliTrack> = emptyList(),
    val playback: PlaybackCheckpoint = PlaybackCheckpoint(),
    val dark: Boolean? = null,
    val followArtwork: Boolean = true,
)
