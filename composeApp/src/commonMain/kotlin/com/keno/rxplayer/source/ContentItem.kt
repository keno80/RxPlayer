package com.keno.rxplayer.source

import com.keno.rxplayer.MusicSource
import io.ktor.http.Url
import io.ktor.http.URLProtocol

data class ContentItem(
    val source: MusicSource, val id: String, val title: String, val author: String,
    val artworkUrl: String?, val durationSeconds: Int,
) {
    val stableKey: String get() = "${source.name}:$id"
}

data class SearchPage(val items: List<ContentItem>, val page: Int, val hasMore: Boolean)
data class VideoPart(val number: Int, val cid: Long, val title: String, val durationSeconds: Int)
data class VideoDetails(val item: ContentItem, val parts: List<VideoPart>, val selectedPart: VideoPart)

// Transient engine input, never persisted as the identity of a library/queue item.
class AudioStream(val url: String, val headers: Map<String, String>, val mime: String, val codec: String, val expiresAtEpochSeconds: Long?) {
    override fun toString(): String = "AudioStream(mime=$mime, codec=$codec, url=<redacted>)"
}

enum class SourceFailureKind { Network, Unavailable, LoginRequired, Restricted, RateLimited, InvalidResponse, UnsupportedAudio }
class SourceFailure(val kind: SourceFailureKind, val code: Int? = null) : Exception("SourceFailure($kind, code=$code)")

data class BilibiliReference(val id: String, val part: Int = 1) {
    companion object {
        fun parse(input: String): BilibiliReference {
            var value = input.trim()
            var part = 1
            if ("://" in value) {
                val url = try { Url(value) } catch (_: Exception) { throw IllegalArgumentException("invalid_video_url") }
                require(url.protocol == URLProtocol.HTTPS && url.host in setOf("www.bilibili.com", "m.bilibili.com", "bilibili.com") && url.user == null && url.password == null && url.port == 443) { "unsupported_video_url" }
                value = Regex("/video/([^/]+)/?").matchEntire(url.encodedPath)?.groupValues?.get(1) ?: throw IllegalArgumentException("unsupported_video_path")
                val parts = url.parameters.getAll("p") ?: listOf("1")
                require(parts.size == 1 && Regex("[1-9][0-9]{0,5}").matches(parts.single())) { "invalid_part" }
                part = parts.single().toInt()
            }
            if (Regex("BV[A-Za-z0-9]{10}").matches(value)) return BilibiliReference(value, part)
            require(Regex("av[1-9][0-9]{0,18}", RegexOption.IGNORE_CASE).matches(value)) { "invalid_video_id" }
            return BilibiliReference("av" + value.drop(2), part)
        }
    }
}
