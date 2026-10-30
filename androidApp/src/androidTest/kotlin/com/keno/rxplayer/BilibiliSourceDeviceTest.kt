package com.keno.rxplayer

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keno.rxplayer.source.BilibiliReference
import com.keno.rxplayer.source.BilibiliSource
import com.keno.rxplayer.source.SourceFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class BilibiliSourceDeviceTest {
    @Test fun anonymousSearchPartsAndAudioBytes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSourceProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject().put("platform", "Android").put("api", Build.VERSION.SDK_INT).put("auth", "anonymous")
        try {
            runBlocking {
                val source = BilibiliSource()
                try {
                    val page = source.search("Blender")
                    assertTrue(page.items.isNotEmpty())
                    report.put("search_count", page.items.size)
                    val detail = source.details(BilibiliReference("BV14u41147YH", 2))
                    assertTrue(detail.parts.size >= 2)
                    assertEquals(2, detail.selectedPart.number)
                    report.put("part_count", detail.parts.size)
                    val audio = JSONArray()
                    for (part in detail.parts.take(2)) {
                        val stream = source.resolve(detail.item.id, part.cid)
                        val connection = URL(stream.url).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 20_000
                            connection.readTimeout = 20_000
                            stream.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                            connection.setRequestProperty("Range", "bytes=0-65535")
                            assertEquals(206, connection.responseCode)
                            val bytes = ByteArray(65536)
                            var size = 0
                            connection.inputStream.use { input ->
                                while (size < bytes.size) {
                                    val count = input.read(bytes, size, bytes.size - size)
                                    if (count < 0) break
                                    size += count
                                }
                            }
                            assertTrue(size >= 32)
                            assertTrue(bytes.copyOfRange(0, 32).decodeToString().contains("ftyp"))
                            audio.put(JSONObject().put("part", part.number).put("cid", part.cid).put("http_status", connection.responseCode).put("bytes_read", size).put("mime", stream.mime))
                        } finally { connection.disconnect() }
                    }
                    report.put("audio_parts", audio).put("passed", true)
                } finally { source.close() }
            }
        } catch (failure: Throwable) {
            val kind = if (failure is SourceFailure) failure.kind.name else failure.javaClass.simpleName
            report.put("passed", false).put("failure", kind)
            // Transport causes can contain the signed URL. Keep the device report bounded.
            throw AssertionError("Bilibili source device probe failed: $kind")
        } finally {
            context.filesDir.resolve("bilibili-source-probe.json").writeText(report.toString(2))
        }
    }
}
