package com.keno.rxplayer.source

import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.*

class BilibiliLiveTest {
    @Test fun anonymousSearchPartsAndAudioBytes() {
        assumeTrue("Explicit opt-in required for real source requests", System.getenv("RXPLAYER_BILIBILI_PROBE") == "1")
        val result = linkedMapOf<String, JsonElement>("checked_at" to JsonPrimitive(Instant.now().toString()), "auth" to JsonPrimitive("anonymous"))
        try {
            runBlocking {
                val client = createSourceHttpClient()
                val source = BilibiliSource(client)
                try {
                    val page = source.search("Blender", 1)
                    assertTrue(page.items.isNotEmpty())
                    result["search_count"] = JsonPrimitive(page.items.size)
                    val detail = source.details(BilibiliReference("BV14u41147YH", 2))
                    assertTrue(detail.parts.size >= 2)
                    result["part_count"] = JsonPrimitive(detail.parts.size)
                    assertEquals(2, detail.selectedPart.number)
                    val audio = detail.parts.take(2).map { part ->
                        val stream = source.resolve(detail.item.id, part.cid)
                        assertEquals("audio/mp4", stream.mime)
                        val status = client.prepareGet(stream.url) {
                            stream.headers.forEach { (name, value) -> header(name, value) }
                            header("Range", "bytes=0-65535")
                        }.execute { response ->
                            assertEquals(206, response.status.value)
                            val bytes = response.bodyAsChannel().readRemaining(65536).readByteArray()
                            assertTrue(bytes.size >= 32)
                            assertTrue(bytes.copyOfRange(0, 32).decodeToString().contains("ftyp"))
                            buildJsonObject {
                                put("part", part.number); put("cid", part.cid)
                                put("http_status", response.status.value); put("bytes_read", bytes.size)
                                put("mime", stream.mime); put("deadline_present", stream.expiresAtEpochSeconds != null)
                            }
                        }
                        status
                    }
                    result["audio_parts"] = JsonArray(audio)
                    result["passed"] = JsonPrimitive(true)
                } finally { source.close() }
            }
        } catch (failure: Throwable) {
            result["passed"] = JsonPrimitive(false)
            result["failure"] = JsonPrimitive(if (failure is SourceFailure) failure.kind.name else failure::class.simpleName)
            if (failure is SourceFailure && failure.code != null) result["failure_code"] = JsonPrimitive(failure.code)
            // Do not expose transport exception causes containing signed media URLs.
            throw AssertionError("Bilibili live probe failed: " + result["failure"])
        } finally {
            System.getenv("RXPLAYER_BILIBILI_REPORT")?.let { destination ->
                val path = Path.of(destination)
                Files.createDirectories(path.parent)
                Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonObject(result)))
            }
        }
    }
}
