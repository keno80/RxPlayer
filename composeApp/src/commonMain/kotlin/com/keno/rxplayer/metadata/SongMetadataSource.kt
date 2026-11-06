package com.keno.rxplayer.metadata

import com.keno.rxplayer.LyricLine
import com.keno.rxplayer.source.sourceHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.io.encoding.Base64
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
        val results = SongProvider.entries.map { provider -> optional { searchProvider(provider, keyword) } }
        if (results.all { it == null }) throw SongMetadataException()
        return results.filterNotNull().flatten().distinctBy { it.key }
    }

    private suspend fun searchProvider(provider: SongProvider, keyword: String): List<SongCandidate> = when (provider) {
        SongProvider.QQ -> qqSearch(keyword)
        SongProvider.KUGOU -> kugouSearch(keyword)
        SongProvider.NETEASE -> neteaseSearch(keyword)
        SongProvider.LRCLIB -> lrclibSearch(keyword)
    }

    private suspend fun neteaseSearch(keyword: String): List<SongCandidate> {
        val root = netease("/api/search/get", listOf("s" to keyword, "type" to "1", "limit" to "5", "offset" to "0"))
        val result = root["result"] as? JsonObject ?: throw SongMetadataException()
        val songs = result["songs"] as? JsonArray
            ?: if (result.number("songCount") == 0L) return emptyList() else throw SongMetadataException()
        return songs.take(5).mapNotNull { (it as? JsonObject)?.toSong() }.distinctBy { it.id }
    }

    suspend fun load(candidate: SongCandidate): SongMatch {
        val original = candidate.copy(artworkUrl = trustedArtwork(candidate.artworkUrl))
        if (!original.validIdentity()) return SongMatch(original)
        var song = if (original.provider == SongProvider.NETEASE) optional { neteaseDetail(original) } ?: original else original
        val related = mutableMapOf<SongProvider, SongCandidate?>()
        suspend fun matching(provider: SongProvider): SongCandidate? {
            if (song.provider == provider) return song
            if (provider !in related) {
                related[provider] = optional { searchProvider(provider, "${song.artists.joinToString(" / ")} - ${song.title}") }
                    ?.filter { artistSetsMatch(it, song) &&
                        sameRecording(song, it.title, it.durationMs.toDouble()) }?.singleOrNull()
            }
            return related[provider]
        }
        if (song.artworkUrl == null) {
            for (provider in listOf(SongProvider.QQ, SongProvider.KUGOU, SongProvider.NETEASE)) {
                val other = matching(provider) ?: continue
                var cover = other.artworkUrl
                if (cover == null && provider == SongProvider.NETEASE && song.provider != SongProvider.NETEASE) {
                    optional { neteaseDetail(other) }?.let { detail ->
                        val verified = detail.takeIf { artistSetsMatch(it, song) && sameRecording(song, it.title, it.durationMs.toDouble()) }
                        related[provider] = verified
                        cover = verified?.artworkUrl
                    }
                }
                if (cover != null) { song = song.copy(artworkUrl = cover); break }
            }
        }
        val qq = optional { matching(SongProvider.QQ)?.let { qqLyrics(it) } }.orEmpty()
        if (qq.isNotEmpty()) return SongMatch(song, qq, "QQ音乐")
        val kugou = optional { kugouLyrics(song) }.orEmpty()
        if (kugou.isNotEmpty()) return SongMatch(song, kugou, "酷狗音乐")
        val primary = optional {
            val neteaseSong = matching(SongProvider.NETEASE) ?: return@optional emptyList()
            val root = netease("/api/song/lyric", listOf("id" to neteaseSong.id.toString(), "lv" to "-1", "kv" to "-1", "tv" to "-1"))
            parseLrc((root["lrc"] as? JsonObject)?.text("lyric").orEmpty())
        }.orEmpty()
        if (primary.isNotEmpty()) return SongMatch(song, primary, "网易云音乐")
        val fallback = optional { fallbackLyrics(song) }.orEmpty()
        return SongMatch(song, fallback, if (fallback.isEmpty()) null else "LRCLIB")
    }

    private suspend fun neteaseDetail(original: SongCandidate): SongCandidate {
        val root = netease("/api/song/detail/", listOf("id" to original.id.toString(), "ids" to "[${original.id}]"))
        val row = (root["songs"] as? JsonArray)?.singleOrNull() as? JsonObject
        return row?.toSong()?.takeIf { it.id == original.id }?.let {
            it.copy(album = it.album.ifBlank { original.album }, artworkUrl = it.artworkUrl ?: original.artworkUrl)
        } ?: original
    }

    private suspend fun qqSearch(keyword: String): List<SongCandidate> {
        // The Desktop method currently returns empty results anonymously. Mobile requires v without cv.
        val payload = buildJsonObject {
            putJsonObject("comm") {
                put("ct", "11"); put("v", "20080508"); put("tmeLoginType", "0"); put("tmeLoginMethod", "0")
            }
            putJsonObject("req") {
                put("module", "music.search.SearchCgiService"); put("method", "DoSearchForQQMusicMobile")
                putJsonObject("param") {
                    put("query", keyword)
                    put("highlight", 1); put("searchid", ""); put("sub_searchid", 0); put("search_type", 0)
                    put("nqc_flag", 0); put("sin", 0); put("ein", 30); put("page_num", 1); put("num_per_page", 10)
                    put("cat", 2); put("grp", 1); put("remoteplace", ""); put("from", ""); put("multi_zhida", 1)
                    put("sem", 0); put("related_data", ""); put("ambi_data", "")
                }
            }
        }
        val root = request("https://u.y.qq.com/cgi-bin/musicu.fcg", emptyList(), qq = true, body = payload.toString()) as? JsonObject
            ?: throw SongMetadataException()
        val req = root["req"] as? JsonObject ?: throw SongMetadataException()
        if (root.number("code") != 0L || req.number("code") != 0L) throw SongMetadataException()
        val rows = ((req["data"] as? JsonObject)?.get("body") as? JsonObject)?.get("item_song") as? JsonArray
            ?: throw SongMetadataException()
        return rows.take(10).mapNotNull { (it as? JsonObject)?.toQqSong() }
    }

    private suspend fun qqLyrics(song: SongCandidate): List<LyricLine> {
        val lyric = request("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg", listOf(
            "songmid" to song.providerId!!, "format" to "json", "nobase64" to "1", "g_tk" to "5381"), qq = true) as? JsonObject
            ?: throw SongMetadataException()
        if (lyric.number("code") != 0L) throw SongMetadataException()
        return parseLrc(lyric.text("lyric").orEmpty())
    }

    private suspend fun kugouLyrics(song: SongCandidate): List<LyricLine> {
        val root = request("https://lyrics.kugou.com/search", listOf("ver" to "1", "client" to "pc", "man" to "yes",
            "keyword" to "${song.artists.joinToString(" / ")} - ${song.title}", "duration" to song.durationMs.toString()) +
            if (song.provider == SongProvider.KUGOU) listOf("hash" to song.providerId!!) else emptyList()) as? JsonObject
            ?: throw SongMetadataException()
        if (root.number("status") != 200L) throw SongMetadataException()
        val rows = root["candidates"] as? JsonArray ?: throw SongMetadataException()
        val match = rows.mapNotNull { it as? JsonObject }.filter { row ->
            val title = row.text("song") ?: return@filter false
            val artist = row.text("singer") ?: return@filter false
            val duration = row.number("duration") ?: return@filter false
            val id = row.text("id") ?: return@filter false
            val accessKey = row.text("accesskey") ?: return@filter false
            id.matches(Regex("[0-9]{1,30}")) && accessKey.matches(Regex("[A-Za-z0-9_-]{1,128}")) &&
                artistsMatch(artist, song.artists) && sameRecording(song, title, duration.toDouble())
        }.singleOrNull() ?: return emptyList()
        val lyric = request("https://lyrics.kugou.com/download", listOf("ver" to "1", "client" to "pc",
            "id" to match.text("id")!!, "accesskey" to match.text("accesskey")!!, "fmt" to "lrc", "charset" to "utf8")) as? JsonObject
            ?: throw SongMetadataException()
        if (lyric.number("status") != 200L) throw SongMetadataException()
        val content = lyric.text("content") ?: return emptyList()
        val decoded = try { Base64.decode(content).decodeToString(throwOnInvalidSequence = true) }
            catch (_: IllegalArgumentException) { throw SongMetadataException() }
            catch (_: CharacterCodingException) { throw SongMetadataException() }
        return parseLrc(decoded)
    }

    private fun sameRecording(song: SongCandidate, title: String, durationMs: Double, versionTitle: String = title): Boolean =
        songKey(title) == songKey(song.title) && songVersions(versionTitle) == songVersions(song.title) &&
            durationMs.isFinite() && durationMs > 0 &&
            durationMs <= 86_400_000 && song.durationMs > 0 && abs(durationMs - song.durationMs) <= 2000

    private fun String.withoutHighlights() = replace("<em>", "").replace("</em>", "")

    private suspend fun kugouSearch(keyword: String): List<SongCandidate> {
        val root = request("https://songsearch.kugou.com/song_search_v2", listOf("keyword" to keyword, "page" to "1",
            "pagesize" to "5", "platform" to "WebFilter", "filter" to "2", "iscorrection" to "1", "privilege_filter" to "0")) as? JsonObject
            ?: throw SongMetadataException()
        if (root.number("status") != 1L) throw SongMetadataException()
        val rows = (root["data"] as? JsonObject)?.get("lists") as? JsonArray ?: throw SongMetadataException()
        return rows.take(5).mapNotNull { (it as? JsonObject)?.toKugouSong() }.distinctBy { it.key }
    }

    private suspend fun lrclibSearch(keyword: String): List<SongCandidate> {
        val rows = lrclibRequest("/api/search", listOf("q" to keyword)) as? JsonArray ?: throw SongMetadataException()
        return rows.take(5).mapNotNull { (it as? JsonObject)?.toLrclibSong() }.distinctBy { it.key }
    }

    private suspend fun netease(path: String, parameters: List<Pair<String, String>>): JsonObject {
        val root = request("https://music.163.com$path", parameters) as? JsonObject ?: throw SongMetadataException()
        if (root.number("code") != 200L) throw SongMetadataException()
        return root
    }

    private suspend fun fallbackLyrics(song: SongCandidate): List<LyricLine> {
        if (song.provider == SongProvider.LRCLIB) {
            val row = lrclibRequest("/api/get/${song.id}", emptyList()) as? JsonObject ?: return emptyList()
            if (row.number("id") != song.id || !lrclibMatches(row, song)) return emptyList()
            return parseLrc(row.text("syncedLyrics").orEmpty())
        }
        val rows = lrclibRequest("/api/search", listOf("track_name" to song.title, "artist_name" to song.artists.joinToString(" "))) as? JsonArray
            ?: return emptyList()
        val matches = rows.mapNotNull { it as? JsonObject }.filter { lrclibMatches(it, song) }
        // Even identical-looking duplicate rows are ambiguous; do not silently choose one recording.
        return parseLrc(matches.singleOrNull()?.text("syncedLyrics").orEmpty())
    }

    private fun lrclibMatches(row: JsonObject, song: SongCandidate): Boolean {
        val title = row.text("trackName") ?: return false
        val artist = row.text("artistName") ?: return false
        val duration = (row["duration"] as? JsonPrimitive)?.doubleOrNull ?: return false
        return artistsMatch(artist, song.artists) && sameRecording(song, title, duration * 1000)
    }

    private suspend fun lrclibRequest(path: String, parameters: List<Pair<String, String>>): JsonElement? = fallbackMutex.withLock {
        // A rate-limited request returns immediately. A later user action may try after the cooldown.
        if (fallbackCooldown?.hasPassedNow() == false) return@withLock null
        lastFallbackFinished?.let { delay((300 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)) }
        try {
            request("https://lrclib.net$path", parameters, isFallback = true)
        } finally {
            lastFallbackFinished = timeSource.markNow()
        }
    }

    private suspend fun request(address: String, parameters: List<Pair<String, String>>, isFallback: Boolean = false,
        qq: Boolean = false, body: String? = null): JsonElement {
        try {
            return client.prepareRequest(address) {
                method = if (body == null) HttpMethod.Get else HttpMethod.Post
                if (body != null) setBody(TextContent(body, ContentType.Application.FormUrlEncoded))
                url { parameters.forEach { (key, value) -> this.parameters.append(key, value) } }
                headers.remove(HttpHeaders.Referrer)
                if (qq) headers[HttpHeaders.Referrer] = "https://y.qq.com/"
                headers[HttpHeaders.UserAgent] = when { isFallback -> LRCLIB_USER_AGENT; qq -> "QQMusic 20080508(android)"; else -> "RxPlayer/0.1" }
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

    private fun JsonObject.toQqSong(): SongCandidate? {
        val mid = text("mid")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,64}")) } ?: return null
        var title = (text("title")?.takeIf { it.isNotBlank() } ?: text("name"))?.withoutHighlights() ?: return null
        val subtitle = text("subtitle")?.withoutHighlights().orEmpty()
        if (songVersions("$title $subtitle") != songVersions(title)) title += " ($subtitle)"
        val artists = namedArtists("singer") ?: return null
        val duration = number("interval")?.takeIf { it in 1..86_400 }?.times(1000)?.toInt() ?: return null
        val album = this["album"] as? JsonObject
        val albumMid = album?.text("mid")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,64}")) }
        val cover = albumMid?.let { "https://y.gtimg.cn/music/photo_new/T002R500x500M000$it.jpg" }
        return title.takeIf { it.isNotBlank() && it.length <= 300 }?.let {
            SongCandidate(number("id")?.coerceAtLeast(0) ?: 0, it, artists, album?.text("name")?.withoutHighlights()?.take(300).orEmpty(),
                duration, cover, SongProvider.QQ, mid)
        }
    }

    private fun JsonObject.toKugouSong(): SongCandidate? {
        val hash = text("FileHash")?.takeIf { it.matches(Regex("[A-Fa-f0-9]{32}")) }?.uppercase() ?: return null
        var title = text("SongName")?.withoutHighlights() ?: return null
        val suffix = text("Suffix")?.withoutHighlights().orEmpty()
        if (suffix.isNotBlank() && !title.endsWith(suffix)) title += " $suffix"
        val artists = if (this["Singers"] is JsonArray) namedArtists("Singers") ?: return null
            else text("SingerName")?.withoutHighlights()?.split('、')?.map(String::trim)?.takeIf { it.isNotEmpty() && it.all { name -> name.isNotBlank() && name.length <= 200 } }
                ?: return null
        val duration = number("Duration")?.takeIf { it in 1..86_400 }?.times(1000)?.toInt() ?: return null
        val cover = trustedArtwork(text("AlbumImage")?.replace("{size}", "500"))
            ?: text("Image")?.replace("{size}", "500")?.takeIf { "/stdmusic/" in it || "/albumcover/" in it }?.let(::trustedArtwork)
        return title.takeIf { it.isNotBlank() && it.length <= 300 }?.let {
            SongCandidate(number("MixSongID")?.coerceAtLeast(0) ?: 0, it, artists, text("AlbumName")?.withoutHighlights()?.take(300).orEmpty(),
                duration, cover, SongProvider.KUGOU, hash)
        }
    }

    private fun JsonObject.toLrclibSong(): SongCandidate? {
        val id = number("id")?.takeIf { it > 0 } ?: return null
        val title = text("trackName")?.takeIf { it.isNotBlank() && it.length <= 300 } ?: return null
        val artist = text("artistName")?.takeIf { it.isNotBlank() && it.length <= 200 } ?: return null
        val seconds = (this["duration"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it in 0.001..86_400.0 } ?: return null
        return SongCandidate(id, title, listOf(artist), text("albumName")?.take(300).orEmpty(), (seconds * 1000).toInt(), provider = SongProvider.LRCLIB)
    }

    private fun JsonObject.namedArtists(key: String): List<String>? {
        val rows = (this[key] as? JsonArray)?.takeIf { it.size in 1..30 } ?: return null
        return rows.map { row -> (row as? JsonObject)?.text("name")?.withoutHighlights()
            ?.takeIf { it.isNotBlank() && it.length <= 200 } ?: return null }.distinct()
    }

    private fun SongCandidate.validIdentity() = when (provider) {
        SongProvider.QQ -> providerId?.matches(Regex("[A-Za-z0-9]{1,64}")) == true
        SongProvider.KUGOU -> providerId?.matches(Regex("[A-Fa-f0-9]{32}")) == true
        SongProvider.NETEASE, SongProvider.LRCLIB -> id > 0
    }

    private fun trustedArtwork(value: String?): String? {
        if (value == null || value.length > 2048) return null
        val url = runCatching { Url(value) }.getOrNull() ?: return null
        if (url.protocol !in listOf(URLProtocol.HTTP, URLProtocol.HTTPS) ||
            !(url.host == "music.126.net" || url.host.endsWith(".music.126.net") ||
                url.host in setOf("y.gtimg.cn", "imge.kugou.com", "imgessl.kugou.com")) ||
            url.user != null || url.password != null || url.port != url.protocol.defaultPort) return null
        return URLBuilder(url).apply { protocol = URLProtocol.HTTPS; port = URLProtocol.HTTPS.defaultPort }.buildString()
    }

    private fun artistsMatch(value: String, artists: List<String>): Boolean {
        val expected = artists.map(::songKey).toSet()
        if (expected.size == 1 && songKey(value) == expected.single()) return true
        return value.split(Regex("[,，、;；&＆/|]|\\b(?:feat|ft)\\.?\\s", RegexOption.IGNORE_CASE))
            .map(::songKey).filter { it.isNotEmpty() }.toSet() == expected
    }

    private fun artistSetsMatch(first: SongCandidate, second: SongCandidate): Boolean {
        if (first.artists.map(::songKey).toSet() == second.artists.map(::songKey).toSet()) return true
        // LRCLIB supplies a single raw credit. Interpret separators only against a known complete singer array.
        return (first.provider == SongProvider.LRCLIB && first.artists.size == 1 && artistsMatch(first.artists.single(), second.artists)) ||
            (second.provider == SongProvider.LRCLIB && second.artists.size == 1 && artistsMatch(second.artists.single(), first.artists))
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    fun close() = client.close()

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val LRCLIB_USER_AGENT = "RxPlayer v0.1 (https://github.com/keno80/RxPlayer)"
    }
}
