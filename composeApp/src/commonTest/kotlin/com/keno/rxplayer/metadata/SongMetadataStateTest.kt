package com.keno.rxplayer.metadata

import com.keno.rxplayer.*
import com.keno.rxplayer.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SongMetadataStateTest {
    private val track = BilibiliTrack(ContentItem(MusicSource.Bilibili, "BV14u41147YH", "歌手《原创歌曲》", "发布者", null, 100), VideoPart(1, 101, "正片", 100))
    private val song = SongCandidate(1, "原创歌曲", listOf("歌手"), "原创专辑", 100000)
    private val match = SongMatch(song, listOf(LyricLine(1000,"原创测试")), "网易云")
    private fun details(t: BilibiliTrack) = VideoDetails(t.item, listOf(t.part), t.part)

    @Test fun automaticMatchRunsOnceAndUsesSeparateDisplayWithoutChangingSourceTrack() = runTest {
        var requests = 0
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { requests++; listOf(song) }, loadSong = { match })
        state.auto(track)!!.join()
        assertNull(state.auto(track))
        assertEquals(1, requests)
        assertEquals("原创歌曲", state.display(track).title)
        assertEquals(track.id, state.display(track).id)
        assertEquals("歌手《原创歌曲》", track.title)
        assertEquals(track.item, (state.display(track) as BilibiliTrack).item)
    }

    @Test fun resetSurvivesRecreationAndCannotBeOverriddenByLateAutomaticOrManualResponse() = runTest {
        val pending = CompletableDeferred<SongMatch>()
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(song) }, loadSong = { withContext(NonCancellable) { pending.await() } })
        val automatic = state.auto(track)!!; runCurrent()
        state.open(track); runCurrent()
        state.restoreOriginal()
        pending.complete(match); automatic.join(); runCurrent()
        assertEquals(track.title, state.display(track).title)
        assertTrue(state.savedRecords.single().automaticDisabled)
        var requested = false
        val restored = SongMetadataState(backgroundScope, state.savedRecords, details = { requested = true; details(it) }, searchSongs = { emptyList() }, loadSong = { match })
        assertNull(restored.auto(track))
        assertFalse(requested)
    }

    @Test fun newerManualSearchAndClosingPanelIgnoreNonCooperativeOldRequests() = runTest {
        val pending = CompletableDeferred<List<SongCandidate>>()
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { if (it == "old") withContext(NonCancellable) { pending.await() } else listOf(song) }, loadSong = { match })
        state.open(track); runCurrent()
        state.query = "old"; val old = state.search()!!; runCurrent()
        state.query = "new"; state.search()!!.join()
        pending.complete(listOf(song.copy(id=2))); old.join()
        assertEquals(listOf(song), state.candidates)
        state.apply(song)!!.join()
        assertNull(state.target)
        assertEquals(match, state.savedRecords.single().match)
        state.open(track); state.close()
        assertNull(state.target)
        assertEquals(match, state.savedRecords.single().match)
    }

    @Test fun coverMayBeSelectedButWrongVersionNeverReceivesOriginalSynchronizedLyrics() = runTest {
        val cover = track.copy(item = track.item.copy(title = "歌手《原创歌曲》翻唱"))
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(song) }, loadSong = { match })
        state.open(cover); runCurrent(); state.apply(song)!!.join()
        assertEquals(song, state.savedRecords.single().match!!.song)
        assertTrue(state.savedRecords.single().match!!.lyrics.isEmpty())
    }

    @Test fun failuresDoNotWriteFakeMatchesAndManualFailureUsesExistingToast() = runTest {
        val messages=mutableListOf<String>()
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { error("network") }, loadSong = { match }, notify = messages::add)
        state.auto(track)!!.join()
        assertTrue(messages.isEmpty()); assertTrue(state.savedRecords.isEmpty())
        state.open(track); runCurrent()
        assertEquals(1, messages.size); assertFalse(state.searching)
        assertTrue(state.savedRecords.isEmpty())
    }

    @Test fun automaticMatchingRechecksIdentityAfterLoadingCurrentCatalogDetails() = runTest {
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(song) },
            loadSong = { match.copy(song = song.copy(title = "另一首歌", artists = listOf("另一个歌手"))) })
        state.auto(track)!!.join()
        assertTrue(state.savedRecords.isEmpty())
    }

    @Test fun closingManualApplyAndSwitchingTracksDiscardLateResponses() = runTest {
        val pending = CompletableDeferred<SongMatch>()
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(song) },
            loadSong = { withContext(NonCancellable) { pending.await() } })
        state.open(track); runCurrent()
        val applying = state.apply(song)!!; runCurrent()
        state.close(); state.open(track.copy(part = track.part.copy(cid = 102))); runCurrent()
        pending.complete(match); applying.join(); runCurrent()
        assertTrue(state.savedRecords.isEmpty())
        assertEquals(102, state.target?.part?.cid)
    }

    @Test fun manualSelectionReadsDescriptionAndKeepsCoverWhenVideoDetailsFail() = runTest {
        val cover = track.copy(item = track.item.copy(title = "我们…怎么两清《原创歌曲》"))
        val state = SongMetadataState(backgroundScope, details = { details(it).copy(description = "原唱：歌手\n翻唱：另一位演唱者") },
            searchSongs = { listOf(song) }, loadSong = { match })
        state.open(cover); runCurrent(); state.apply(song)!!.join()
        assertTrue(state.savedRecords.single().match!!.lyrics.isEmpty())
        val offline = SongMetadataState(backgroundScope, details = { error("video unavailable") }, searchSongs = { listOf(song) }, loadSong = { match })
        offline.open(track); runCurrent(); offline.apply(song)!!.join()
        assertEquals(song, offline.savedRecords.single().match!!.song)
        assertTrue(offline.savedRecords.single().match!!.lyrics.isEmpty())
    }

    @Test fun manualSelectionDistinguishesEqualNumericIdsAcrossProviders() = runTest {
        val qq = song.copy(provider = SongProvider.QQ, providerId = "mid1")
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(song, qq) },
            loadSong = { SongMatch(it) })
        state.open(track); runCurrent()
        assertNull(state.apply(qq.copy(providerId = "not-in-results")))
        state.apply(qq)!!.join()
        assertEquals(qq, state.savedRecords.single().match!!.song)
    }

    @Test fun actualFlowerSeaTitleDoesNotClearLoadedQqLyricsBeforeDisplay() = runTest {
        val video = BilibiliTrack(ContentItem(MusicSource.Bilibili, "BV1E23n6QEBs", "花海 - 周杰伦 【无损音质】", "大西洋电台", null, 266), VideoPart(1, 40415661055, "花海 - 周杰伦 【无损音质】", 266))
        val selected = SongCandidate(449198, "花海", listOf("周杰伦"), "魔杰座", 264000, provider = SongProvider.QQ, providerId = "003cI52o4daJJL")
        val loaded = SongMatch(selected, listOf(LyricLine(1000, "原创测试")), "QQ音乐")
        val state = SongMetadataState(backgroundScope, details = { details(it) }, searchSongs = { listOf(selected) }, loadSong = { loaded })
        state.open(video); runCurrent(); state.apply(selected)!!.join()
        assertEquals(loaded, state.savedRecords.single().match)
        assertEquals(loaded.lyrics, state.display(video).lyrics)
        var refreshes = 0
        val cached = loaded.copy(lyrics = emptyList(), lyricsSource = null)
        val restored = SongMetadataState(backgroundScope, listOf(SongMetadataRecord(video.id, cached, true)), details = { details(it) },
            searchSongs = { error("Keep the user's selected song") }, loadSong = { refreshes++; loaded })
        assertNotNull(restored.auto(video)).join()
        assertEquals(loaded, restored.savedRecords.single().match)
        assertTrue(restored.savedRecords.single().automaticDisabled)
        assertNull(restored.auto(video))
        assertEquals(1, refreshes)
    }
}
