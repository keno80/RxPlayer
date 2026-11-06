package com.keno.rxplayer.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SongCatalogSourceTest {
    private val qq = """{"code":0,"req":{"code":0,"data":{"body":{"item_song":[{"id":42,"mid":"QQMID42","name":"原创歌曲","title":"原创歌曲 (Live)","interval":100,"singer":[{"name":"歌手"}],"album":{"name":"现场专辑","mid":"ALBUM42"}}]}}}}"""
    private val kg = """{"status":1,"data":{"lists":[{"MixSongID":"42","FileHash":"0123456789ABCDEF0123456789ABCDEF","SongName":"原创歌曲","SingerName":"歌手","Singers":[{"name":"歌手"}],"Duration":100,"AlbumName":"专辑","Image":"http://imge.kugou.com/stdmusic/{size}/cover.jpg"}]}}"""
    private val ne = """{"code":200,"result":{"songs":[{"id":42,"name":"原创歌曲","artists":[{"name":"歌手"}],"duration":100000,"album":{"name":"专辑"}}]}}"""
    private val lrc = """[{"id":42,"trackName":"原创歌曲","artistName":"歌手","albumName":"专辑","duration":100}]"""

    @Test fun searchAggregatesAllCatalogsInPriorityOrderAndKeepsRecordingVersions() = runTest {
        val hosts = mutableListOf<String>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            hosts += request.url.host
            respond(when (request.url.host) {
                "u.y.qq.com" -> qq
                "songsearch.kugou.com" -> kg
                "music.163.com" -> ne
                "lrclib.net" -> lrc
                else -> error("Unexpected host")
            })
        }))
        try {
            val rows = source.search("原创歌曲 歌手")
            assertEquals(4, rows.size)
            assertEquals(4, rows.map { it.key }.distinct().size)
            assertEquals(SongProvider.entries.toList(), rows.map { it.provider })
            assertEquals(listOf("u.y.qq.com", "songsearch.kugou.com", "music.163.com", "lrclib.net"), hosts)
            assertEquals("原创歌曲 (Live)", rows.first().title)
            assertTrue(rows[0].artworkUrl!!.startsWith("https://y.gtimg.cn/"))
            assertEquals("https://imge.kugou.com/stdmusic/500/cover.jpg", rows[1].artworkUrl)
        } finally { source.close() }
    }

    @Test fun qqFailureDoesNotPreventKugouCandidatesWhenNeteaseAlsoFails() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "songsearch.kugou.com" -> respond(kg)
                "lrclib.net" -> respond("[]")
                else -> respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
        }))
        try { assertEquals("原创歌曲", source.search("原创歌曲").single().title) } finally { source.close() }
    }

    @Test fun allProvidersUnavailableReportFailureInsteadOfPretendingNoMatches() = runTest {
        val source = SongMetadataSource(HttpClient(MockEngine { respond("no", HttpStatusCode.ServiceUnavailable) }))
        try { assertFailsWith<SongMetadataException> { source.search("原创歌曲") } } finally { source.close() }
    }

    @Test fun qqSelectionLoadsItsOwnCoverAndLyricsWithoutDependingOnNetease() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000,
            "https://y.gtimg.cn/music/photo_new/cover.jpg", SongProvider.QQ, "QQMID42")
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            assertEquals("c.y.qq.com", request.url.host)
            assertEquals("QQMID42", request.url.parameters["songmid"])
            respond("""{"code":0,"lyric":"[00:01.00]原创测试"}""")
        }))
        try {
            val result = source.load(selected)
            assertEquals(selected, result.song)
            assertEquals("QQ音乐", result.lyricsSource)
        } finally { source.close() }
    }

    @Test fun kugouSelectionUsesItsHashWhileQqRemainsTheFirstLyricsChoice() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000,
            "https://imge.kugou.com/stdmusic/500/cover.jpg", SongProvider.KUGOU, "0123456789ABCDEF0123456789ABCDEF")
        val hosts = mutableListOf<String>()
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            hosts += request.url.host
            when (request.url.host) {
                "u.y.qq.com" -> respond("""{"code":0,"req":{"code":0,"data":{"body":{"item_song":[]}}}}""")
                "lyrics.kugou.com" -> if (request.url.encodedPath == "/search") {
                    assertEquals(selected.providerId, request.url.parameters["hash"])
                    respond("""{"status":200,"candidates":[{"id":"123","accesskey":"key","song":"原创歌曲","singer":"歌手","duration":100000}]}""")
                } else respond("""{"status":200,"content":"WzAwOjAxLjAwXXRlc3Q="}""")
                else -> error("Kugou match must not use a NetEase ID")
            }
        }))
        try {
            assertEquals("酷狗音乐", source.load(selected).lyricsSource)
            assertEquals(listOf("u.y.qq.com", "lyrics.kugou.com", "lyrics.kugou.com"), hosts)
        } finally { source.close() }
    }

    @Test fun neteaseFallbackSearchesTheRecordingBeforeUsingItsOwnId() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000,
            "https://y.gtimg.cn/music/photo_new/cover.jpg", SongProvider.QQ, "QQMID42")
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "c.y.qq.com" -> respond("""{"code":0,"lyric":""}""")
                "lyrics.kugou.com" -> respond("""{"status":200,"candidates":[]}""")
                "music.163.com" -> if (request.url.encodedPath.contains("search")) respond(ne.replace("\"id\":42", "\"id\":89"))
                    else {
                        assertEquals("/api/song/lyric", request.url.encodedPath)
                        assertEquals("89", request.url.parameters["id"])
                        respond("""{"code":200,"lrc":{"lyric":"[00:01.00]原创测试"}}""")
                    }
                else -> error("Unexpected provider")
            }
        }))
        try {
            val result = source.load(selected)
            assertEquals(selected, result.song)
            assertEquals("网易云音乐", result.lyricsSource)
        } finally { source.close() }
    }

    @Test fun lrclibSelectionFetchesExactRecordAndDoesNotInventACover() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000, provider = SongProvider.LRCLIB)
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "u.y.qq.com" -> respond("""{"code":0,"req":{"code":0,"data":{"body":{"item_song":[]}}}}""")
                "songsearch.kugou.com" -> respond("""{"status":1,"data":{"lists":[]}}""")
                "lyrics.kugou.com" -> respond("""{"status":200,"candidates":[]}""")
                "music.163.com" -> { assertTrue(request.url.encodedPath.contains("search")); respond("""{"code":200,"result":{"songs":[]}}""") }
                "lrclib.net" -> {
                    assertEquals("/api/get/42", request.url.encodedPath)
                    respond("""{"id":42,"trackName":"原创歌曲","artistName":"歌手","duration":100,"syncedLyrics":"[00:01.00]原创测试"}""")
                }
                else -> error("Unexpected provider")
            }
        }))
        try {
            val result = source.load(selected)
            assertNull(result.song.artworkUrl)
            assertEquals("LRCLIB", result.lyricsSource)
        } finally { source.close() }
    }

    @Test fun missingCoverUsesOnlyAnUnambiguousMatchingRecordingFromOtherCatalogs() = runTest {
        for ((kgResponse, expected) in listOf(kg to true, kg.replace("原创歌曲", "原创歌曲 (Live)") to false,
            kg.replace("\"歌手\"", "\"另一歌手\"") to false)) {
            val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000, provider = SongProvider.QQ, providerId = "QQMID42")
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when (request.url.host) {
                    "songsearch.kugou.com" -> respond(kgResponse)
                    "music.163.com" -> respond("""{"code":200,"result":{"songs":[]}}""")
                    "c.y.qq.com" -> respond("""{"code":0,"lyric":"[00:01.00]原创测试"}""")
                    else -> error("Unexpected provider")
                }
            }))
            try {
                val result = source.load(selected)
                assertEquals(expected, result.song.artworkUrl != null)
                assertEquals(selected.key, result.song.key)
            } finally { source.close() }
        }
    }

    @Test fun lrclibCombinedArtistCreditMatchesTheCompleteQqSingerSetWithoutRewritingBandNames() = runTest {
        for (artists in listOf(listOf("歌手甲", "歌手乙"), listOf("Earth, Wind & Fire"))) {
            val credit = artists.joinToString(", ")
            val selected = SongCandidate(42, "原创歌曲", listOf(credit), "专辑", 100000, provider = SongProvider.LRCLIB)
            val singers = artists.joinToString(",") { """{"name":"$it"}""" }
            val source = SongMetadataSource(HttpClient(MockEngine { request ->
                when (request.url.host) {
                    "u.y.qq.com" -> respond("""{"code":0,"req":{"code":0,"data":{"body":{"item_song":[{"mid":"QQMID42","name":"原创歌曲","interval":100,"singer":[$singers],"album":{"mid":"ALBUM42"}}]}}}}""")
                    "c.y.qq.com" -> respond("""{"code":0,"lyric":"[00:01.00]原创测试"}""")
                    else -> error("The complete QQ recording should provide artwork and lyrics")
                }
            }))
            try {
                val result = source.load(selected)
                assertEquals(selected.key, result.song.key)
                assertEquals(listOf(credit), result.song.artists)
                assertTrue(result.song.artworkUrl!!.startsWith("https://y.gtimg.cn/"))
                assertEquals("QQ音乐", result.lyricsSource)
            } finally { source.close() }
        }
    }

    @Test fun authoritativeBandNamesAreNotSplitIntoUnrelatedIndividualSingers() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("Earth, Wind & Fire"), "专辑", 100000,
            provider = SongProvider.QQ, providerId = "QQMID42")
        val wrongSingers = """[{"name":"Earth"},{"name":"Wind"},{"name":"Fire"}]"""
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "songsearch.kugou.com" -> respond(kg.replace("[{\"name\":\"歌手\"}]", wrongSingers))
                "music.163.com" -> respond(ne.replace("[{\"name\":\"歌手\"}]", wrongSingers))
                "c.y.qq.com" -> respond("""{"code":0,"lyric":"[00:01.00]原创测试"}""")
                else -> error("Unexpected provider")
            }
        }))
        try { assertNull(source.load(selected).song.artworkUrl) } finally { source.close() }
    }

    @Test fun aChangedNeteaseDetailCannotSupplyAnotherRecordingCover() = runTest {
        val selected = SongCandidate(42, "原创歌曲", listOf("歌手"), "专辑", 100000,
            provider = SongProvider.QQ, providerId = "QQMID42")
        val source = SongMetadataSource(HttpClient(MockEngine { request ->
            when (request.url.host) {
                "songsearch.kugou.com" -> respond("""{"status":1,"data":{"lists":[]}}""")
                "music.163.com" -> if (request.url.encodedPath.contains("search")) respond(ne)
                    else respond("""{"code":200,"songs":[{"id":42,"name":"另一录音","artists":[{"name":"歌手"}],"duration":100000,"album":{"picUrl":"https://p1.music.126.net/other.jpg"}}]}""")
                "c.y.qq.com" -> respond("""{"code":0,"lyric":"[00:01.00]原创测试"}""")
                else -> error("Unexpected provider")
            }
        }))
        try { assertNull(source.load(selected).song.artworkUrl) } finally { source.close() }
    }
}
