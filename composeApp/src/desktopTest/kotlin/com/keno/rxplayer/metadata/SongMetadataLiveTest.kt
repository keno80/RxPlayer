package com.keno.rxplayer.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Explicit opt-in only; the normal suite never contacts external music services. */
class SongMetadataLiveTest {
    @Test fun anonymousSearchAndArtworkContract() {
        assumeTrue("Set RXPLAYER_METADATA_LIVE_TEST=1 to contact music services", System.getenv("RXPLAYER_METADATA_LIVE_TEST") == "1")
        runBlocking {
            val source = SongMetadataSource()
            try {
                val songs = source.search("李佳薇 甲乙丙丁")
                val selected = assertNotNull(songs.firstOrNull {
                    songKey(songBaseTitle(it.title)) == songKey("甲乙丙丁") && "李佳薇" in it.artists
                })
                assertTrue(selected.durationMs > 0)
                val match = source.load(selected)
                assertEquals(selected.id, match.song.id)
                assertTrue(assertNotNull(match.song.artworkUrl).startsWith("https://"))
                // Lyric absence is a valid service result; never log the copyrighted response body.
            } finally { source.close() }
        }
    }
}
