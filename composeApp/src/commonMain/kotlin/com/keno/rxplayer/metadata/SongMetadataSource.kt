package com.keno.rxplayer.metadata

import com.keno.rxplayer.source.sourceHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Deliberately omits server URLs, response bodies, and the original exception cause. */
class SongMetadataException : Exception("歌曲资料暂时不可用")

class SongMetadataSource(private val client: HttpClient = HttpClient(sourceHttpEngine()) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
}, private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val fallbackMutex = Mutex()
    private var lastFallbackFinished: TimeMark? = null
    private var fallbackCooldown: TimeMark? = null

    suspend fun search(query: String): List<SongCandidate> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        if (keyword.length > 200) throw SongMetadataException()
        val root = netease("/api/search/get", listOf("s" to keyword, "type" to "1", "limit" to "5", "offset" to "0"))
        val result = root["result"] as? JsonObject ?: throw SongMetadataException()
        val songs = result["songs"] as? JsonArray
            ?: if (result.number("songCount") == 0L) return emptyList() else throw SongMetadataException()
        return songs.take(5).mapNotNull { (it as? JsonObject)?.toSong() }.distinctBy { it.id }
    }

    suspend fun load(candidate: SongCandidate): SongMatch {
        val original = candidate.copy(artworkUrl = trustedArtwork(candidate.artworkUrl))
        if (original.id <= 0) return SongMatch(original)
        val song = optional {
            val root = netease("/api/song/detail/", listOf("id" to original.id.toString(), "ids" to "[${original.id}]"))
            val row = (root["songs"] as? JsonArray)?.singleOrNull() as? JsonObject
            row?.toSong()?.takeIf { it.id == original.id }?.let {
                it.copy(album = it.album.ifBlank { original.album }, artworkUrl = it.artworkUrl ?: original.artworkUrl)
            }
        } ?: original
        val primary = optional {
            val root = netease("/api/song/lyric", listOf("id" to song.id.toString(), "lv" to "-1", "kv" to "-1", "tv" to "-1"))
            parseLrc((root["lrc"] as? JsonObject)?.text("lyric").orEmpty())
        }.orEmpty()
        if (primary.isNotEmpty()) return SongMatch(song, primary, "网易云音乐")
        val fallback = optional { fallbackLyrics(song) }.orEmpty()
        return SongMatch(song, fallback, if (fallback.isEmpty()) null else "LRCLIB")
    }

    private suspend fun netease(path: String, parameters: List<Pair<String, String>>): JsonObject {
        val root = request("https://music.163.com$path", parameters) as? JsonObject ?: throw SongMetadataException()
        if (root.number("code") != 200L) throw SongMetadataException()
        return root
    }

    private suspend fun fallbackLyrics(song: SongCandidate) = fallbackMutex.withLock {
        // A rate-limited request returns immediately. A later user action may try after the cooldown.
        if (fallbackCooldown?.hasPassedNow() == false) return@withLock emptyList()
        lastFallbackFinished?.let { delay((300 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)) }
        try {
            val rows = request("https://lrclib.net/api/search", listOf("track_name" to song.title,
                "artist_name" to song.artists.joinToString(" ")), isFallback = true) as? JsonArray
                ?: throw SongMetadataException()
            val matches = rows.mapNotNull { it as? JsonObject }.filter { row ->
                val title = row.text("trackName") ?: return@filter false
                val artist = row.text("artistName") ?: return@filter false
                val duration = (row["duration"] as? JsonPrimitive)?.doubleOrNull ?: return@filter false
                songKey(title) == songKey(song.title) &&
                    songVersions(title) == songVersions(song.title) && artistsMatch(artist, song.artists) &&
                    duration.isFinite() && duration > 0 && song.durationMs > 0 &&
                    abs(duration * 1000 - song.durationMs) <= 2000
            }
            // Even identical-looking duplicate rows are ambiguous; do not silently choose one recording.
            parseLrc(matches.singleOrNull()?.text("syncedLyrics").orEmpty())
        } finally {
            lastFallbackFinished = timeSource.markNow()
        }
    }

    private suspend fun request(address: String, parameters: List<Pair<String, String>>, isFallback: Boolean = false): JsonElement {
        try {
            return client.prepareGet(address) {
                url { parameters.forEach { (key, value) -> this.parameters.append(key, value) } }
                headers.remove(HttpHeaders.Referrer)
                headers[HttpHeaders.UserAgent] = if (isFallback) LRCLIB_USER_AGENT else "RxPlayer/0.1"
            }.execute { response ->
                if (isFallback && response.status.value == 429) {
                    val retry = response.headers[HttpHeaders.RetryAfter]?.trim()
                    val cooldown = retry?.toLongOrNull()?.takeIf { it >= 0 }?.seconds
                        ?: retry?.let { value -> runCatching { (value.fromHttpToGmtDate().timestamp - GMTDate().timestamp).coerceAtLeast(0).milliseconds }.getOrNull() }
                        ?: 60.seconds
                    fallbackCooldown = timeSource.markNow() + cooldown
                    throw SongMetadataException()
                }
                if (response.status.value !in 200..299 ||
                    (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > MAX_RESPONSE_BYTES) throw SongMetadataException()
                val bytes = response.bodyAsChannel().readRemaining(MAX_RESPONSE_BYTES + 1L).readByteArray()
                if (bytes.size > MAX_RESPONSE_BYTES) throw SongMetadataException()
                Json.parseToJsonElement(bytes.decodeToString())
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            throw SongMetadataException()
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: SongMetadataException) { null }

    private fun JsonObject.toSong(): SongCandidate? {
        val id = number("id")?.takeIf { it > 0 } ?: return null
        val title = text("name")?.takeIf { it.isNotBlank() && it.length <= 300 } ?: return null
        val artists = (this["artists"] as? JsonArray)?.takeIf { it.size in 1..30 }?.map {
            (it as? JsonObject)?.text("name")?.takeIf { name -> name.isNotBlank() && name.length <= 200 } ?: return null
        }?.distinct()?.takeIf { it.isNotEmpty() } ?: return null
        val duration = number("duration")?.takeIf { it in 1..86_400_000L }?.toInt() ?: return null
        val album = this["album"] as? JsonObject
        return SongCandidate(id, title, artists, album?.text("name")?.take(300).orEmpty(), duration,
            trustedArtwork(album?.text("picUrl")))
    }

    private fun trustedArtwork(value: String?): String? {
        if (value == null || value.length > 2048) return null
        val url = runCatching { Url(value) }.getOrNull() ?: return null
        if (url.protocol !in listOf(URLProtocol.HTTP, URLProtocol.HTTPS) ||
            !(url.host == "music.126.net" || url.host.endsWith(".music.126.net")) ||
            url.user != null || url.password != null || url.port != url.protocol.defaultPort) return null
        return URLBuilder(url).apply { protocol = URLProtocol.HTTPS; port = URLProtocol.HTTPS.defaultPort }.buildString()
    }

    private fun artistsMatch(value: String, artists: List<String>): Boolean {
        val expected = artists.map(::songKey).toSet()
        if (expected.size == 1 && songKey(value) == expected.single()) return true
        return value.split(Regex("[,，、;；&＆/|]|\\b(?:feat|ft)\\.?\\s", RegexOption.IGNORE_CASE))
            .map(::songKey).filter { it.isNotEmpty() }.toSet() == expected
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    fun close() = client.close()

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val LRCLIB_USER_AGENT = "RxPlayer v0.1 (https://github.com/keno80/RxPlayer)"
    }
}
