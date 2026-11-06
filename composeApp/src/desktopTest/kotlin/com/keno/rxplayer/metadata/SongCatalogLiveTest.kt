package com.keno.rxplayer.metadata

import com.keno.rxplayer.audio.fetchSystemArtwork
import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.source.BilibiliSource
import com.keno.rxplayer.source.BilibiliReference
import com.keno.rxplayer.source.sourceHttpEngine
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class SongCatalogLiveTest {
    @Test fun realQqFlowerSeaCandidateHasItsOwnCoverAndTimedLyrics() {
        assumeTrue("Opt-in catalog requests", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val source = SongMetadataSource()
            val http = HttpClient(sourceHttpEngine())
            try {
                val rows = source.search("周杰伦 花海")
                assertEquals(rows.map { it.provider.ordinal }.sorted(), rows.map { it.provider.ordinal })
                val selected = assertNotNull(rows.firstOrNull { it.provider == SongProvider.QQ && it.id == 449198L })
                assertEquals("花海", selected.title)
                assertEquals(listOf("周杰伦"), selected.artists)
                val match = source.load(selected)
                assertEquals(selected.key, match.song.key)
                assertEquals("QQ音乐", match.lyricsSource)
                assertTrue(match.lyrics.size > 10)
                val cover = assertNotNull(fetchSystemArtwork(http, match.song.artworkUrl, null))
                assertEquals("y.gtimg.cn", Url(cover.url).host)
                val bili = BilibiliSource()
                try {
                    val details = bili.details(BilibiliReference("BV1E23n6QEBs"))
                    val video = BilibiliTrack(details.item, details.selectedPart, details.parts.size)
                    val state = SongMetadataState(this, listOf(SongMetadataRecord(video.id, SongMatch(selected), true)),
                        details = { details }, searchSongs = { error("Preserve the manual selection") }, loadSong = source::load)
                    assertNotNull(state.auto(video)).join()
                    val repaired = assertNotNull(state.savedRecords.single().match)
                    assertEquals(selected.key, repaired.song.key)
                    assertTrue(repaired.lyrics.size > 10)
                    assertTrue(state.savedRecords.single().automaticDisabled)
                    println("RxPlayer lyrics repair live: video=BV1E23n6QEBs, timed_lines=${repaired.lyrics.size}, selected_song_preserved=true")
                } finally { bili.close() }
                println("RxPlayer catalog live: provider=QQ, song=449198, cover_bytes=${cover.bytes.size}, timed_lines=${match.lyrics.size}, provider_identity_preserved=true")
            } finally { source.close(); http.close() }
        }
    }

    @Test fun realKugouCandidateKeepsKugouCoverAndLoadsPriorityLyrics() {
        assumeTrue("Opt-in catalog requests", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val source = SongMetadataSource()
            val http = HttpClient(sourceHttpEngine())
            try {
                val selected = assertNotNull(source.search("周杰伦 晴天").firstOrNull {
                    it.provider == SongProvider.KUGOU && it.title == "晴天" && it.artists == listOf("周杰伦") && it.durationMs == 269000
                })
                val match = source.load(selected)
                assertEquals(selected.key, match.song.key)
                assertTrue(match.lyrics.isNotEmpty())
                val cover = assertNotNull(fetchSystemArtwork(http, match.song.artworkUrl, null))
                assertTrue(Url(cover.url).host.endsWith(".kugou.com"))
                println("RxPlayer catalog live: provider=Kugou, cover_bytes=${cover.bytes.size}, timed_lines=${match.lyrics.size}, lyrics_source=${match.lyricsSource}, provider_identity_preserved=true")
            } finally { source.close(); http.close() }
        }
    }
}
