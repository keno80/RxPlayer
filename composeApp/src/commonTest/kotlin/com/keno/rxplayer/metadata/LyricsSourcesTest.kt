package com.keno.rxplayer.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class LyricsSourcesTest {
    private val song = SongCandidate(42, "晴天", listOf("周杰伦"), "叶惠美", 269_000)
    private val emptyQq = qqSearch("")
    private fun qqRow(title: String = "晴天", artist: String = "周杰伦", seconds: Int = 269, subtitle: String = "") =
        """{"mid":"testMID","name":"$title","subtitle":"$subtitle","interval":$seconds,"singer":[{"name":"$artist"}]}"""
    private fun qqSearch(rows: String) = """{"code":0,"req":{"code":0,"data":{"body":{"item_song":[$rows]}}}}"""
    private fun kgRow(title: String = "晴天", artist: String = "周杰伦", ms: Int = 269_000) =
        """{"id":"123","accesskey":"test-key","song":"$title","singer":"$artist","duration":$ms}"""
    private fun kgSearch(rows: String) = """{"status":200,"candidates":[$rows]}"""
    private val kgLrc = """{"status":200,"content":"${Base64.encode("[00:02.00]酷狗测试".encodeToByteArray())}"}"""
    private val netease = """{"code":200,"lrc":{"lyric":"[00:03.00]网易测试"}}"""

    @Test fun qqWinsAndSearchUsesAnonymousMobileContract() = runTest {
        val calls = mutableListOf<String>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            calls += request.url.host
            when (request.url.host) {
                "u.y.qq.com" -> {
                    val payload = Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
                    assertEquals("11", payload["comm"]!!.jsonObject["ct"]!!.jsonPrimitive.content)
                    assertNull(payload["comm"]!!.jsonObject["cv"])
                    val req = payload["req"]!!.jsonObject
                    assertEquals("DoSearchForQQMusicMobile", req["method"]!!.jsonPrimitive.content)
                    assertEquals("周杰伦 - 晴天", req["param"]!!.jsonObject["query"]!!.jsonPrimitive.content)
                    assertNull(request.headers[HttpHeaders.Cookie])
                    respond(qqSearch(qqRow(title = "<em>晴天</em>")))
                }
                "c.y.qq.com" -> {
                    assertEquals("testMID", request.url.parameters["songmid"])
                    assertEquals("1", request.url.parameters["nobase64"])
                    assertEquals("https://y.qq.com/", request.headers[HttpHeaders.Referrer])
                    respond("""{"code":0,"lyric":"[00:01.00]QQ测试"}""")
                }
                "music.163.com" -> { assertTrue(request.url.encodedPath.contains("detail")); respond("{}") }
                else -> error("lower-priority provider called")
            }
        }))
        try {
            val match = source.load(song)
            assertEquals("QQ音乐", match.lyricsSource)
            assertEquals("QQ测试", match.lyrics.single().text)
            assertEquals(listOf("music.163.com", "u.y.qq.com", "c.y.qq.com"), calls)
        } finally { source.close() }
    }

    @Test fun emptyQqLyricsFallsBackToKugouBeforeNetease() = runTest {
        val calls = mutableListOf<String>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            calls += request.url.host + request.url.encodedPath
            when (request.url.host) {
                "u.y.qq.com" -> respond(qqSearch(qqRow()))
                "c.y.qq.com" -> respond("""{"code":0,"lyric":"纯文本"}""")
                "lyrics.kugou.com" -> if (request.url.encodedPath == "/search") {
                    assertEquals("周杰伦 - 晴天", request.url.parameters["keyword"])
                    assertEquals("269000", request.url.parameters["duration"])
                    assertNull(request.headers[HttpHeaders.Referrer])
                    respond(kgSearch(kgRow()))
                } else {
                    assertEquals("test-key", request.url.parameters["accesskey"])
                    assertEquals("lrc", request.url.parameters["fmt"])
                    respond(kgLrc)
                }
                "music.163.com" -> { assertTrue(request.url.encodedPath.contains("detail")); respond("{}") }
                else -> error("lower-priority provider called")
            }
        }))
        try {
            val match = source.load(song)
            assertEquals("酷狗音乐", match.lyricsSource)
            assertEquals(2000, match.lyrics.single().atMs)
            assertEquals(5, calls.size)
        } finally { source.close() }
    }

    @Test fun crossProviderMismatchAndAmbiguitySkipLyricDownloads() = runTest {
        for ((qq, kg) in listOf(
            qqRow(artist = "其他歌手") to kgRow(artist = "其他歌手"),
            qqRow(title = "晴天 (Live)") to kgRow(title = "晴天 (Live)"),
            qqRow(subtitle = "Live") to kgRow(title = "晴天 (English Version)"),
            qqRow(seconds = 272) to kgRow(ms = 272000),
            qqRow() + "," + qqRow() to kgRow() + "," + kgRow(),
            qqRow(artist = "周杰伦&其他歌手") to kgRow(artist = "周杰伦&其他歌手"),
            qqRow(title = "晴天 (未知录音)") to kgRow(title = "晴天 (未知录音)")
        )) {
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when (request.url.host) {
                    "u.y.qq.com" -> respond(qqSearch(qq))
                    "lyrics.kugou.com" -> { assertEquals("/search", request.url.encodedPath); respond(kgSearch(kg)) }
                    "c.y.qq.com" -> error("mismatch must not download QQ lyrics")
                    else -> respond(if (request.url.encodedPath.contains("detail")) "{}" else netease)
                }
            }))
            try { assertEquals("网易云音乐", source.load(song).lyricsSource) } finally { source.close() }
        }
    }

    @Test fun unavailableOrMalformedProvidersContinueToNetease() = runTest {
        for (bad in listOf("not json", """{"status":200,"content":"%%%"}""", """{"status":200,"content":"/w=="}""",
            """{"status":403,"content":"ignored"}""")) {
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when (request.url.host) {
                    "u.y.qq.com" -> respond("private", HttpStatusCode.TooManyRequests)
                    "lyrics.kugou.com" -> respond(if (request.url.encodedPath == "/search") kgSearch(kgRow()) else bad)
                    else -> respond(if (request.url.encodedPath.contains("detail")) "{}" else netease)
                }
            }))
            try { assertEquals("网易云音乐", source.load(song).lyricsSource) } finally { source.close() }
        }
    }

    @Test fun matchingVersionAndTwoSecondBoundaryRemainEligible() = runTest {
        val live = song.copy(title = "晴天 (Live)", artists = listOf("周杰伦", "歌手乙"))
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "u.y.qq.com" -> respond(qqSearch(qqRow(title = "晴天 (Live)", seconds = 271)
                    .replace("[{\"name\":\"周杰伦\"}]", "[{\"name\":\"歌手乙\"},{\"name\":\"周杰伦\"}]")))
                "c.y.qq.com" -> respond("""{"code":0,"lyric":"[00:01.00]测试"}""")
                "music.163.com" -> respond("{}")
                else -> error("exact match must stop the chain")
            }
        }))
        try { assertEquals("QQ音乐", source.load(live).lyricsSource) } finally { source.close() }
    }

    @Test fun allSourcesEmptyLeaveCoverAndReturnNoFakeLyrics() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "u.y.qq.com" -> respond(emptyQq)
                "lyrics.kugou.com" -> respond(kgSearch(""))
                "lrclib.net" -> respond("[]")
                else -> respond("""{"code":200,"lrc":{"lyric":""}}""")
            }
        }))
        try {
            val result = source.load(song)
            assertEquals(song, result.song)
            assertNull(result.lyricsSource)
            assertTrue(result.lyrics.isEmpty())
        } finally { source.close() }
    }

    @Test fun cancellationInEachNewProviderStopsTheChain() = runTest {
        for (host in listOf("u.y.qq.com", "lyrics.kugou.com")) {
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                if (request.url.host == host) throw CancellationException("cancel")
                if (request.url.host == "u.y.qq.com") respond(emptyQq) else respond("{}")
            }))
            try { assertFailsWith<CancellationException> { source.load(song) } } finally { source.close() }
        }
    }
}
