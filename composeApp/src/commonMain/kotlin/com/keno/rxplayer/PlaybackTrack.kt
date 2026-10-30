package com.keno.rxplayer

import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.VideoPart

sealed interface PlaybackTrack {
    val id: String
    val title: String
    val artist: String
    val subtitle: String
    val source: MusicSource
    val artwork: Int
    val artworkUrl: String? get() = null
    val lyrics: List<LyricLine> get() = emptyList()
    val durationMs: Int get() = 0
}

data class BilibiliTrack(val item: ContentItem, val part: VideoPart, val partCount: Int = 1) : PlaybackTrack {
    override val id: String get() = "${item.stableKey}:${part.cid}"
    override val title: String get() = if (partCount > 1) "${item.title} · P${part.number} ${part.title}" else item.title
    override val artist: String get() = item.author
    override val subtitle: String get() = part.title
    override val source: MusicSource get() = MusicSource.Bilibili
    override val artwork: Int get() = 0
    override val artworkUrl: String? get() = item.artworkUrl
    override val durationMs: Int get() = part.durationSeconds * 1000
}
