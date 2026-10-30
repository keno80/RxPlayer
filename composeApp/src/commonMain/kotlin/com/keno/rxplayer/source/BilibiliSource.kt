package com.keno.rxplayer.source

import com.keno.rxplayer.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class BilibiliSource(private val client: HttpClient = createSourceHttpClient()) {
    private val sessionMutex = Mutex()
    private var sessionReady = false

    suspend fun search(query: String, page: Int = 1): SearchPage {
        require(query.isNotBlank() && query.length <= 200 && page in 1..1000) { "invalid_search" }
        val data = api("/x/web-interface/search/type", mapOf("search_type" to "video", "keyword" to query.trim(), "page" to page.toString(), "page_size" to "20"))
        val rows = data["result"] as? JsonArray ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
        val items = rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.text("bvid")?.takeIf { Regex("BV[A-Za-z0-9]{10}").matches(it) } ?: return@mapNotNull null
            ContentItem(MusicSource.Bilibili, id, cleanTitle(row.text("title").orEmpty()), row.text("author").orEmpty(), artwork(row.text("pic")), duration(row.text("duration")))
        }
        return SearchPage(items, page, page < (data.int("numPages") ?: page))
    }

    suspend fun details(reference: BilibiliReference): VideoDetails {
        val canonical = BilibiliReference.parse(reference.id)
        require(reference.part > 0) { "invalid_part" }
        val data = api("/x/web-interface/view", if (canonical.id.startsWith("BV")) mapOf("bvid" to canonical.id) else mapOf("aid" to canonical.id.drop(2)))
        val id = data.text("bvid") ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
        val parts = (data["pages"] as? JsonArray)?.map { element ->
            val row = element as? JsonObject ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
            VideoPart(row.int("page") ?: throw SourceFailure(SourceFailureKind.InvalidResponse), row.long("cid") ?: throw SourceFailure(SourceFailureKind.InvalidResponse), row.text("part").orEmpty(), row.int("duration") ?: 0)
        } ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
        val selected = parts.firstOrNull { it.number == reference.part } ?: throw IllegalArgumentException("part_out_of_range")
        val item = ContentItem(MusicSource.Bilibili, id, data.text("title").orEmpty(), (data["owner"] as? JsonObject)?.text("name").orEmpty(), artwork(data.text("pic")), data.int("duration") ?: 0)
        return VideoDetails(item, parts, selected)
    }

    suspend fun resolve(bvid: String, cid: Long): AudioStream {
        require(Regex("BV[A-Za-z0-9]{10}").matches(bvid) && cid > 0) { "invalid_content_identity" }
        val data = api("/x/player/playurl", mapOf("bvid" to bvid, "cid" to cid.toString(), "fnval" to "16", "fnver" to "0", "qn" to "64"))
        val audio = ((data["dash"] as? JsonObject)?.get("audio") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.text("mimeType") == "audio/mp4" && it.text("codecs").orEmpty().startsWith("mp4a.") }
            .maxByOrNull { it.long("bandwidth") ?: 0 } ?: throw SourceFailure(SourceFailureKind.UnsupportedAudio)
        val raw = audio.text("baseUrl") ?: audio.text("base_url") ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
        val url = try { Url(raw) } catch (_: Exception) { throw SourceFailure(SourceFailureKind.InvalidResponse) }
        // Bilibili's MCDN returned a valid HTTPS :8082 URL in the live probe.
        // Trust the allowed CDN origin and TLS scheme, not a hard-coded port 443.
        if (url.protocol != URLProtocol.HTTPS || url.user != null || url.password != null || !listOf("bilivideo.com", "bilivideo.cn", "bilivideo.net", "akamaized.net").any { url.host == it || url.host.endsWith(".$it") }) throw SourceFailure(SourceFailureKind.InvalidResponse)
        return AudioStream(raw, mapOf(HttpHeaders.UserAgent to BILIBILI_USER_AGENT, HttpHeaders.Referrer to "https://www.bilibili.com/"), "audio/mp4", audio.text("codecs").orEmpty(), url.parameters["deadline"]?.toLongOrNull())
    }

    private suspend fun ensureSession() = sessionMutex.withLock {
        if (!sessionReady) {
            guarded {
                client.prepareGet("https://www.bilibili.com/").execute { checkStatus(it.status.value) }
                sessionReady = true
            }
        }
    }

    private suspend fun api(path: String, params: Map<String, String>): JsonObject {
        ensureSession()
        return guarded {
            client.prepareGet("https://api.bilibili.com$path") { url { params.forEach { (key, value) -> parameters.append(key, value) } } }.execute { response ->
                checkStatus(response.status.value)
                val bytes = response.bodyAsChannel().readRemaining(4 * 1024 * 1024 + 1L).readByteArray()
                if (bytes.size > 4 * 1024 * 1024) throw SourceFailure(SourceFailureKind.InvalidResponse)
                val root = try { Json.parseToJsonElement(bytes.decodeToString()).jsonObject } catch (_: Exception) { throw SourceFailure(SourceFailureKind.InvalidResponse) }
                val code = root.int("code") ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
                if (code != 0) throw SourceFailure(when (code) {
                    -101 -> SourceFailureKind.LoginRequired
                    -404, 62002, 62004 -> SourceFailureKind.Unavailable
                    -412, -352 -> SourceFailureKind.Restricted
                    else -> SourceFailureKind.InvalidResponse
                }, code)
                root["data"] as? JsonObject ?: throw SourceFailure(SourceFailureKind.InvalidResponse)
            }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: SourceFailure) { throw failure }
        catch (_: Exception) { throw SourceFailure(SourceFailureKind.Network) }

    private fun checkStatus(status: Int) {
        if (status !in 200..299) throw SourceFailure(when (status) {
            401 -> SourceFailureKind.LoginRequired
            403, 412 -> SourceFailureKind.Restricted
            404, 410 -> SourceFailureKind.Unavailable
            429 -> SourceFailureKind.RateLimited
            else -> SourceFailureKind.Network
        }, status)
    }

    fun close() = client.close()
}

private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
private fun artwork(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let {
    when { it.startsWith("//") -> "https:$it"; it.startsWith("http://") -> "https://" + it.removePrefix("http://"); it.startsWith("https://") -> it; else -> null }
}
private fun cleanTitle(value: String): String = value.replace(Regex("<[^>]*>"), "")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
private fun duration(value: String?): Int {
    val parts = value?.split(":") ?: return 0
    if (parts.size !in 1..3) return 0
    return parts.fold(0) { total, part -> total * 60 + (part.trim().toIntOrNull()?.coerceIn(0, 9999) ?: return 0) }
}
