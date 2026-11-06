package com.keno.rxplayer.metadata

import com.keno.rxplayer.*
import com.keno.rxplayer.source.*
import kotlin.test.*

class SongMetadataTest {
    private fun track(title: String, part: String = title, count: Int = 1, seconds: Int = 211) = BilibiliTrack(
        ContentItem(MusicSource.Bilibili, "BV14u41147YH", title, "JLRS-LeoFM", "https://i0.hdslb.com/video.jpg", seconds), VideoPart(1, 101, part, seconds), count)
    private val song = SongCandidate(1, "甲乙丙丁", listOf("李佳薇"), "甲乙丙丁", 210461, "https://p1.music.126.net/album.jpg")

    @Test fun explicitTitleAndDescriptionIdentifySingerRatherThanUploader() {
        val t = track("李佳薇 《甲乙丙丁》百万豪装录音棚大声听")
        val hint = assertNotNull(songHint(t, "《甲乙丙丁》是李佳薇演唱的抒情歌曲"))
        assertEquals("甲乙丙丁", hint.title)
        assertEquals("李佳薇", hint.artist)
        assertEquals(song, autoMatch(hint, t, listOf(song)))
        assertNull(songHint(track("好听的音乐"), ""))
        assertNull(songHint(track("甲乙丙丁"), ""))
    }

    @Test fun versionAndDurationCannotBeIgnoredForAutomaticLyrics() {
        val plain = track("李佳薇《甲乙丙丁》")
        assertNull(autoMatch(songHint(plain, "")!!, plain, listOf(song, song.copy(id = 2))))
        assertNull(autoMatch(songHint(plain, "")!!, plain, listOf(song.copy(durationMs = 200000))))
        val cantonese = track("李佳薇〈甲乙丙丁 Strangers〉粤语版 官方MV")
        assertNull(autoMatch(songHint(cantonese, "")!!, cantonese, listOf(song)))
        val cover = track("我们…怎么两清｜哭腔版《甲乙丙丁》")
        val hint = assertNotNull(songHint(cover, "原唱：李佳薇\n翻唱：黎歌Neeko\n混音：小神"))
        assertEquals("黎歌Neeko", hint.artist)
        assertNull(autoMatch(hint, cover, listOf(song)))
        assertFalse(lyricsFit(cover, song, "原唱：李佳薇\n翻唱：黎歌Neeko"))
        assertFalse(lyricsFit(track("李佳薇《甲乙丙丁》现场"), song))
        assertTrue(lyricsFit(plain, song))
    }

    @Test fun multiPartNeverUsesAnAlbumWideTitleAsTheSelectedSong() {
        assertNull(songHint(track("李佳薇《甲乙丙丁》合集", "幕后片段", 3), "歌手：李佳薇"))
        val part = track("精选合集", "李佳薇 - 甲乙丙丁", 3)
        assertEquals("甲乙丙丁", songHint(part, "")?.title)
        assertEquals("李佳薇", songHint(part, "")?.artist)
    }

    @Test fun extraSingersAndLanguageOrRecordingVariantsAreNeverStrippedIntoAnAutomaticMatch() {
        val plain = track("李佳薇《甲乙丙丁》")
        val hint = songHint(plain)!!
        for (candidate in listOf(song.copy(artists = listOf("李佳薇", "合作歌手")),
            song.copy(title = "甲乙丙丁 (English Version)"), song.copy(title = "甲乙丙丁 (Remastered)"),
            song.copy(title = "甲乙丙丁 (女声版)"))) {
            assertNull(autoMatch(hint, plain, listOf(candidate)))
            assertFalse(lyricsFit(plain, candidate))
        }
    }

    @Test fun unknownParenthesizedEditionRequiresAnExplicitChoiceEvenWithOtherKnownVersionFlags() {
        val plain = track("李佳薇《甲乙丙丁》")
        for (label in listOf("Demo", "未知录音限定", "你我怎么两清")) {
            assertNull(autoMatch(songHint(plain)!!, plain, listOf(song.copy(title = "甲乙丙丁 ($label)"))))
        }
        val english = track("李佳薇《甲乙丙丁》英文版")
        assertNull(autoMatch(songHint(english)!!, english, listOf(song.copy(title = "甲乙丙丁 (English Version) (Mystery recording)"))))
    }

    @Test fun lrcParsesMillisecondsMultipleTagsAndOffsetWithoutInventingTimings() {
        val parsed = parseLrc("[ar:Test]\n[offset:-100]\n[00:01.25][00:03.005]原创第一句\n[00:02.5]原创第二句\n[00:61.00]坏时间\n只有纯文本\n[bad]x")
        assertEquals(listOf(LyricLine(1150,"原创第一句"), LyricLine(2400,"原创第二句"), LyricLine(2905,"原创第一句")), parsed)
        assertTrue(parseLrc("没有时间戳的文字").isEmpty())
        assertTrue(parseLrc("[99:99.99]invalid").isEmpty())
    }

    @Test fun displayOverridePreservesIdentityDurationAndOriginalInformation() {
        val original = track("李佳薇《甲乙丙丁》")
        val display = original.copy(songMetadata = SongMatch(song, listOf(LyricLine(1000, "原创测试歌词")), "网易云"))
        assertEquals(original.id, display.id)
        assertEquals(original.durationMs, display.durationMs)
        assertEquals(original.item, display.item)
        assertEquals("李佳薇", display.artist)
        assertEquals(song.title, display.title)
        assertEquals(song.artworkUrl, display.artworkUrl)
        assertEquals(original.artworkUrl, display.fallbackArtworkUrl)
        val record = SongMetadataRecord(original.id, display.songMetadata, true)
        assertEquals(record, decodeSongRecord(original.id, encodeSongRecord(record)))
    }

    @Test fun automaticSelectionUsesProviderPriorityWithoutMergingAmbiguousVersions() {
        val original = track("李佳薇《甲乙丙丁》")
        val hint = songHint(original)!!
        val qq = song.copy(provider = SongProvider.QQ, providerId = "mid1")
        val kg = song.copy(provider = SongProvider.KUGOU, providerId = "0123456789ABCDEF0123456789ABCDEF")
        assertEquals(qq, autoMatch(hint, original, listOf(song, kg, qq)))
        assertEquals(kg, autoMatch(hint, original, listOf(qq, qq.copy(providerId = "mid2"), kg, song)))
        assertEquals(song, autoMatch(hint, original, listOf(qq.copy(title = "甲乙丙丁 (Live)"), song)))
        assertNotEquals(qq.key, kg.key)
        assertNotEquals(qq.key, song.key)
    }

    @Test fun qualifiedProviderIdentityRoundTripsAndLegacyPayloadStillMeansNetease() {
        for (provider in SongProvider.entries) {
            val selected = song.copy(provider = provider, providerId = if (provider == SongProvider.QQ) "mid42" else null)
            val record = SongMetadataRecord("video", SongMatch(selected), true)
            assertEquals(record, decodeSongRecord("video", encodeSongRecord(record)))
        }
        val legacy = """{"disabled":true,"id":42,"title":"歌名","artists":["歌手"],"album":"专辑","duration":100000,"lyrics":[]}"""
        assertEquals(SongProvider.NETEASE, decodeSongRecord("video", legacy).match!!.song.provider)
    }

    @Test fun rawMultiArtistAndBandCreditsRemainCompatibleWithMatchingVideoCredits() {
        for (credit in listOf("歌手甲 / 歌手乙" to "歌手甲, 歌手乙", "Earth, Wind & Fire" to "Earth, Wind & Fire")) {
            val original = track("${credit.first}《甲乙丙丁》")
            assertTrue(lyricsFit(original, song.copy(artists = listOf(credit.second), provider = SongProvider.LRCLIB)))
        }
    }

    @Test fun songFirstVideoTitleKeepsQqLyricsAndIgnoresOnlyTechnicalQualityTags() {
        val video = track("花海 - 周杰伦 【无损音质】", seconds = 266)
        val selected = SongCandidate(449198, "花海", listOf("周杰伦"), "魔杰座", 264000, provider = SongProvider.QQ, providerId = "003cI52o4daJJL")
        val hint = assertNotNull(songHint(video))
        assertFalse(hint.title.contains("无损音质"))
        assertEquals(selected, autoMatch(hint, video, listOf(selected)))
        assertTrue(lyricsFit(video, selected))
        assertTrue(lyricsFit(video, selected, "歌手：周杰伦"))
        assertFalse(lyricsFit(video, selected, "原唱：周杰伦\n翻唱：另一位歌手"))
        assertFalse(lyricsFit(video.copy(item = video.item.copy(title = "花海 - 周杰伦 【现场版】")), selected))
        assertFalse(lyricsFit(video.copy(part = video.part.copy(durationSeconds = 267)), selected))
    }
}
