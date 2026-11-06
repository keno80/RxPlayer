package com.keno.rxplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.*

class WorkspaceStateTest {
    @Test fun upgradingKeepsOldNavigationAndScrollWithoutPlaylistFields() {
        val state = WorkspaceState.Saver.restore(listOf("Library", 2, 3, 40, 5, 60, 7, 80))!!
        assertEquals(WorkspaceDestination.Library, state.destination)
        assertEquals(2, state.libraryTab)
        assertEquals(3, state.searchScroll.firstVisibleItemIndex)
        assertEquals(60, state.libraryScroll.firstVisibleItemScrollOffset)
        assertEquals(7, state.settingsScroll.firstVisibleItemIndex)
        assertNull(state.playlistId)
        assertNull(state.playlistDialog)
        assertEquals(0, state.playlistScroll.firstVisibleItemIndex)
    }

    @Test fun songListAndEditingContextSurviveSavedStateRoundTrip() {
        val original = WorkspaceState(WorkspaceDestination.Library, 1, playlistId = "list", playlistDialog = "rename", editingPlaylistId = "list", playlistScroll = LazyListState(4, 50))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(WorkspaceState.Saver) { scope.save(original) }!!
        val restored = WorkspaceState.Saver.restore(saved)!!
        assertEquals("list", restored.playlistId)
        assertEquals("rename", restored.playlistDialog)
        assertEquals("list", restored.editingPlaylistId)
        assertEquals(4, restored.playlistScroll.firstVisibleItemIndex)
        assertEquals(50, restored.playlistScroll.firstVisibleItemScrollOffset)
    }
}
