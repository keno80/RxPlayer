package com.keno.rxplayer.library

import com.keno.rxplayer.*
import com.keno.rxplayer.source.*
import kotlin.test.*

class PlaylistStateTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频", "作者", null, 90)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "第一P", 40), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "第二P", 50), 2)

    @Test fun editingKeepsDistinctPartsAndStablePlaylistIdentity() {
        val state = PlaylistState()
        val list = state.create("  通勤  ")
        assertEquals("通勤", list.name)
        assertNotEquals(list.id, state.create("通勤").id)
        state.add(list.id, listOf(first, second, first))
        assertEquals(listOf(first, second), state.lists.first().tracks)
        val original = state.lists.first()
        state.move(list.id, second.id, 0)
        state.rename(list.id, "  夜间  ")
        assertEquals(list.id, state.lists.first().id)
        assertEquals("夜间", state.lists.first().name)
        assertEquals(listOf(second, first), state.lists.first().tracks)
        assertEquals(listOf(first, second), original.tracks)
        state.remove(list.id, first.id)
        assertEquals(listOf(second), state.lists.first().tracks)
        state.delete(list.id)
        assertFalse(state.lists.any { it.id == list.id })
    }

    @Test fun invalidNamesAndMovesLeaveDataUntouched() {
        val state = PlaylistState()
        assertFailsWith<IllegalArgumentException> { state.create("  ") }
        assertFailsWith<IllegalArgumentException> { state.create("a".repeat(41)) }
        val list = state.create("夜航")
        state.add(list.id, listOf(first))
        val before = state.lists
        assertFailsWith<IllegalArgumentException> { state.rename(list.id, " ") }
        assertFailsWith<IllegalArgumentException> { state.move(list.id, first.id, 1) }
        assertEquals(before, state.lists)
    }
}
