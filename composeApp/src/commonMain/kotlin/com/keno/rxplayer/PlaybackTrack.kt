package com.keno.rxplayer

import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.VideoPart
import com.keno.rxplayer.metadata.SongMatch

sealed interface PlaybackTrack {
    val id: String
    val title: String
    val artist: String
    val subtitle: String
    val source: MusicSource
    val artwork: Int
    val artworkUrl: String? get() = null
    val fallbackArtworkUrl: String? get() = null
    val lyrics: List<LyricLine> get() = emptyList()
    val durationMs: Int get() = 0
}

data class BilibiliTrack(val item: ContentItem, val part: VideoPart, val partCount: Int = 1, val songMetadata: SongMatch? = null) : PlaybackTrack {
    override val id: String get() = "${item.stableKey}:${part.cid}"
    override val title: String get() = songMetadata?.song?.title ?: if (partCount > 1) "${item.title} · P${part.number} ${part.title}" else item.title
    override val artist: String get() = songMetadata?.song?.artists?.joinToString(" / ") ?: item.author
    override val subtitle: String get() = part.title
    override val source: MusicSource get() = MusicSource.Bilibili
    override val artwork: Int get() = 0
    override val artworkUrl: String? get() = songMetadata?.song?.artworkUrl ?: item.artworkUrl
    override val fallbackArtworkUrl: String? get() = if (songMetadata?.song?.artworkUrl != null) item.artworkUrl else null
    override val lyrics: List<LyricLine> get() = songMetadata?.lyrics.orEmpty()
    override val durationMs: Int get() = part.durationSeconds * 1000
}
