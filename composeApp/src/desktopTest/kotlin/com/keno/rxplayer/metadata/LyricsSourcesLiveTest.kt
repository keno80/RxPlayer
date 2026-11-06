package com.keno.rxplayer.metadata

import com.keno.rxplayer.source.sourceHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class LyricsSourcesLiveTest {
    private val song = SongCandidate(186016, "晴天", listOf("周杰伦"), "叶惠美", 269_000, "https://p1.music.126.net/cover.jpg")

    @Test fun realQqSearchAndLyricsWinTheProductionChain() {
        assumeTrue("Opt-in real lyrics requests", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val source = SongMetadataSource()
            try {
                val match = source.load(song)
                assertEquals("QQ音乐", match.lyricsSource)
                assertTrue(match.lyrics.size > 10)
                assertEquals(song.id, match.song.id)
                println("RxPlayer lyrics live: source=QQ, timed_lines=${match.lyrics.size}, identity_preserved=true")
            } finally { source.close() }
        }
    }

    @Test fun realKugouSearchAndDownloadWorkWhenQqHasNoMatch() {
        assumeTrue("Opt-in real lyrics requests", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val http = HttpClient(sourceHttpEngine()) {
                followRedirects = false
                install(HttpTimeout) { requestTimeoutMillis = 15_000 }
            }
            var realRequests = 0
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when (request.url.host) {
                    "u.y.qq.com" -> respond("""{"code":0,"req":{"code":0,"data":{"body":{"item_song":[]}}}}""")
                    "music.163.com" -> { assertTrue(request.url.encodedPath.contains("detail")); respond("{}") }
                    "lyrics.kugou.com" -> {
                        realRequests++
                        val response = http.get(request.url) { headers.appendAll(request.headers) }
                        respond(response.bodyAsText(), response.status)
                    }
                    else -> error("Sample must match Kugou before lower-priority providers")
                }
            }))
            try {
                val match = source.load(song)
                assertEquals("酷狗音乐", match.lyricsSource)
                assertTrue(match.lyrics.size > 10)
                assertEquals(2, realRequests)
                println("RxPlayer lyrics live: source=Kugou, timed_lines=${match.lyrics.size}, real_requests=$realRequests, qq_empty_injected=true")
            } finally { source.close(); http.close() }
        }
    }
}
