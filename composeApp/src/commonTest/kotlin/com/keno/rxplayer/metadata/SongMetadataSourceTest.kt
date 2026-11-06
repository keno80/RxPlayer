package com.keno.rxplayer.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SongMetadataSourceTest {
    private val candidate = SongCandidate(42, "甲乙丙丁", listOf("李佳薇"), "专辑", 250_000)
    private val detail = """{"code":200,"songs":[{"id":42,"name":"甲乙丙丁","artists":[{"name":"李佳薇"}],"duration":250000,"album":{"name":"专辑","picUrl":"http://p1.music.126.net/cover.jpg"}}]}"""
    private val noLyrics = """{"code":200,"lrc":{"lyric":""}}"""
    private val lyrics = """{"code":200,"lrc":{"lyric":"[00:01.00]测试歌词"}}"""
    private fun fallback(title: String = "甲乙丙丁", artist: String = "李佳薇", seconds: Double = 250.0) =
        """{"trackName":"$title","artistName":"$artist","albumName":"专辑","duration":$seconds,"syncedLyrics":"[00:02.00]备用歌词"}"""

    @Test fun searchKeepsSingersVersionDurationAndUsesBoundedAnonymousQuery() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            requests += request
            respond("""{"code":200,"result":{"songs":[{"id":42,"name":"甲乙丙丁 (Live)","artists":[{"name":"李佳薇"},{"name":"歌手乙"}],"duration":250000,"album":{"name":"现场专辑"}}]}}""")
        }))
        try {
            val song = source.search("  李佳薇 甲乙丙丁  ").single()
            assertEquals(42L, song.id)
            assertEquals("甲乙丙丁 (Live)", song.title)
            assertEquals(listOf("李佳薇", "歌手乙"), song.artists)
            assertEquals(250_000, song.durationMs)
            assertEquals("现场专辑", song.album)
            assertNull(song.artworkUrl)
            val request = requests.single { it.url.host == "music.163.com" }
            assertEquals("李佳薇 甲乙丙丁", request.url.parameters["s"])
            assertEquals("1", request.url.parameters["type"])
            assertEquals("5", request.url.parameters["limit"])
            assertEquals("0", request.url.parameters["offset"])
            assertEquals("music.163.com", request.url.host)
            assertNull(request.headers[HttpHeaders.Referrer])
        } finally { source.close() }
    }

    @Test fun searchRejectsMalformedRowsAndDuplicateIdentities() = runTest {
        val valid = """{"id":42,"name":"甲乙丙丁","artists":[{"name":"李佳薇"}],"duration":250000,"album":{"name":"专辑"}}"""
        val source = SongMetadataSource(HttpClient(MockEngine {
            respond("""{"code":200,"result":{"songs":[$valid,$valid,{"id":-1,"name":"bad"},{"id":43,"name":"bad","artists":[],"duration":250000}]}}""")
        }))
        try { assertEquals(listOf(candidate), source.search("甲乙丙丁")) } finally { source.close() }
    }

    @Test fun emptySearchDoesNotMakeARequest() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { error("unexpected request") }))
        try { assertTrue(source.search("   ").isEmpty()) } finally { source.close() }
    }

    @Test fun searchFailuresDoNotExposeServerBodyOrUrl() = runTest {
        for ((body, status) in listOf("private signed secret" to HttpStatusCode.ServiceUnavailable,
            """{"code":403,"message":"private signed secret"}""" to HttpStatusCode.OK,
            "not json private signed secret" to HttpStatusCode.OK,
            "x".repeat(1_048_577) to HttpStatusCode.OK)) {
            val source = SongMetadataSource(HttpClient(MockEngine { respond(body, status) }))
            try {
                val error = assertFailsWith<SongMetadataException> { source.search("私人查询") }
                assertFalse(error.toString().contains("secret"))
                assertFalse(error.toString().contains("私人查询"))
                assertFalse(error.toString().contains("https://"))
                assertNull(error.cause)
            } finally { source.close() }
        }
    }

    @Test fun detailAddsTrustedCoverAndPrimaryLyrics() = runTest {
        val paths = mutableListOf<Url>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            paths += request.url
            respond(if (request.url.encodedPath.contains("detail")) detail else lyrics)
        }))
        try {
            val result = source.load(candidate)
            assertEquals("https://p1.music.126.net/cover.jpg", result.song.artworkUrl)
            assertEquals("测试歌词", result.lyrics.single().text)
            assertEquals(1000, result.lyrics.single().atMs)
            assertEquals("网易云音乐", result.lyricsSource)
            assertEquals("42", paths.first().parameters["id"])
            assertEquals("[42]", paths.first().parameters["ids"])
            assertEquals(listOf("music.163.com", "u.y.qq.com", "lyrics.kugou.com", "music.163.com"), paths.map { it.host })
        } finally { source.close() }
    }

    @Test fun detailWrongIdentityDoesNotReplaceCandidateOrBlockLyrics() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.contains("detail")) detail.replace("\"id\":42", "\"id\":99") else lyrics)
        }))
        try {
            val result = source.load(candidate)
            assertEquals(candidate, result.song)
            assertEquals("测试歌词", result.lyrics.single().text)
        } finally { source.close() }
    }

    @Test fun detailAndLyricsFailuresRemainIndependent() = runTest {
        for (detailFails in listOf(false, true)) {
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                if (request.url.host == "lrclib.net") respond("[]")
                else if (request.url.encodedPath.contains("detail")) {
                    if (detailFails) respond("private", HttpStatusCode.ServiceUnavailable) else respond(detail)
                } else if (detailFails) respond(lyrics) else respond("private", HttpStatusCode.ServiceUnavailable)
            }))
            try {
                val result = source.load(candidate)
                if (detailFails) { assertEquals(candidate, result.song); assertEquals(1, result.lyrics.size) }
                else { assertEquals("https://p1.music.126.net/cover.jpg", result.song.artworkUrl); assertTrue(result.lyrics.isEmpty()) }
            } finally { source.close() }
        }
    }

    @Test fun foreignAndCredentialedArtworkUrlsAreDiscarded() = runTest {
        for (url in listOf("https://p1.music.126.net.evil.test/x", "https://evil.test/x", "https://u@p1.music.126.net/x", "file:///x")) {
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                respond(if (request.url.encodedPath.contains("detail")) detail.replace("http://p1.music.126.net/cover.jpg", url) else lyrics)
            }))
            try { assertNull(source.load(candidate).song.artworkUrl) } finally { source.close() }
        }
    }

    @Test fun fallbackRequiresUniqueSingerTitleVersionAndTwoSecondDurationMatch() = runTest {
        for ((rows, expected) in listOf(
            "[${fallback(title = "甲乙丙丁！", seconds = 252.0)}]" to true,
            "[${fallback(artist = "另一歌手")}]" to false,
            "[${fallback(title = "另一歌曲")}]" to false,
            "[${fallback(title = "甲乙丙丁 (Live)")}]" to false,
            "[${fallback(title = "甲乙丙丁 (English Version)")}]" to false,
            "[${fallback(seconds = 252.01)}]" to false,
            "[${fallback()},${fallback()}]" to false
        )) {
            var fallbackRequest: io.ktor.client.request.HttpRequestData? = null
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when {
                    request.url.host == "lrclib.net" -> { fallbackRequest = request; respond(rows) }
                    request.url.encodedPath.contains("detail") -> respond(detail)
                    else -> respond(noLyrics)
                }
            }))
            try {
                val result = source.load(candidate)
                assertEquals(expected, result.lyrics.isNotEmpty(), rows)
                if (expected) assertEquals("LRCLIB", result.lyricsSource) else assertNull(result.lyricsSource)
                val request = assertNotNull(fallbackRequest)
                assertEquals("甲乙丙丁", request.url.parameters["track_name"])
                assertEquals("李佳薇", request.url.parameters["artist_name"])
                assertEquals("RxPlayer v0.1 (https://github.com/keno80/RxPlayer)", request.headers[HttpHeaders.UserAgent])
            } finally { source.close() }
        }
    }

    @Test fun cancellationIsNeverConvertedToAnEmptyMatch() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { throw CancellationException("cancel") }))
        try { assertFailsWith<CancellationException> { source.load(candidate) } } finally { source.close() }
    }

    @Test fun fallbackRequestsAreSerialAndHaveAtLeastThreeHundredMillisecondsBetweenThem() = runTest {
        val starts = mutableListOf<Long>()
        val finishes = mutableListOf<Long>()
        var active = 0
        var maxActive = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                when {
                    request.url.host == "lrclib.net" -> {
                        starts += testScheduler.currentTime
                        active++
                        maxActive = maxOf(maxActive, active)
                        delay(100)
                        active--
                        finishes += testScheduler.currentTime
                        respond("[]")
                    }
                    request.url.encodedPath.contains("detail") -> respond(detail)
                    else -> respond(noLyrics)
                }
            }
        })
        val source = SongMetadataSource(HttpClient(engine), testScheduler.timeSource)
        try {
            val first = async { source.load(candidate) }
            val second = async { source.load(candidate) }
            first.await(); second.await()
            assertEquals(1, maxActive)
            assertEquals(2, starts.size)
            assertTrue(starts[1] - starts[0] >= 300)
            assertTrue(starts[1] - finishes[0] >= 300)
        } finally { source.close() }
    }

    @Test fun fallback429StartsCooldownAndNeverRetriesTheSameLoad() = runTest {
        var fallbackCalls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                when {
                    request.url.host == "lrclib.net" -> {
                        fallbackCalls++
                        respond("private", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "60"))
                    }
                    request.url.encodedPath.contains("detail") -> respond(detail)
                    else -> respond(noLyrics)
                }
            }
        })
        val source = SongMetadataSource(HttpClient(engine), testScheduler.timeSource)
        try {
            assertTrue(source.load(candidate).lyrics.isEmpty())
            assertEquals(1, fallbackCalls)
            assertTrue(source.load(candidate).lyrics.isEmpty())
            assertEquals(1, fallbackCalls)
            advanceTimeBy(60_000)
            assertTrue(source.load(candidate).lyrics.isEmpty())
            assertEquals(2, fallbackCalls)
        } finally { source.close() }
    }
}
