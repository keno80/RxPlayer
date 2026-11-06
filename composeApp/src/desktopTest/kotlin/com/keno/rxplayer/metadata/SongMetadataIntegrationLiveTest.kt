package com.keno.rxplayer.metadata

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.audio.fetchSystemArtwork
import com.keno.rxplayer.source.BilibiliReference
import com.keno.rxplayer.source.BilibiliSource
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class SongMetadataIntegrationLiveTest {
    @Test fun realVideoCluesFindCandidateAndExplicitChoiceLoadsActualCoverAndTimedLyrics() {
        assumeTrue("Opt-in real metadata requests", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val bili = BilibiliSource()
            val catalog = SongMetadataSource()
            val http = HttpClient(com.keno.rxplayer.source.sourceHttpEngine())
            try {
                val details = bili.details(BilibiliReference("BV1Ekub6zE3D"))
                val original = BilibiliTrack(details.item, details.selectedPart, details.parts.size)
                val hint = assertNotNull(songHint(original, details.description))
                assertEquals("李佳薇", hint.artist)
                val candidates = catalog.search("${hint.title} ${hint.artist}")
                // The catalog's parenthetical subtitle is not in the video: keep automatic matching conservative.
                val selected = assertNotNull(candidates.firstOrNull { it.provider == SongProvider.NETEASE && it.id == 3399839173L })
                assertNull(autoMatch(hint, original, listOf(selected)))
                val match = catalog.load(selected)
                assertTrue(lyricsFit(original, match.song, details.description))
                assertTrue(match.lyrics.isNotEmpty(), "This opt-in sample should have timed lyrics")
                val url = assertNotNull(match.song.artworkUrl)
                val image = assertNotNull(fetchSystemArtwork(http, url, original.item.artworkUrl))
                assertTrue(Url(image.url).host.endsWith(".music.126.net"))
                assertTrue(image.bytes.size in 32..256 * 1024)
                val display = original.copy(songMetadata = match)
                assertEquals(original.id, display.id)
                assertEquals(original.durationMs, display.durationMs)
                println("RxPlayer metadata live: video=${details.item.id}, song=${match.song.id}, singer_matched=true, explicit_choice=true, automatic_alias_rejected=true, timed_lines=${match.lyrics.size}, image_decoded=true, image_bytes=${image.bytes.size}, source_identity_preserved=true")
            } finally { http.close(); catalog.close(); bili.close() }
        }
    }
}
