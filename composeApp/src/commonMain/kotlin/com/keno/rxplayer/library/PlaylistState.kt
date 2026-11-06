package com.keno.rxplayer.library

import androidx.compose.runtime.*
import com.keno.rxplayer.BilibiliTrack
import kotlin.uuid.Uuid

/** A playlist holds concrete parts, never expiring playback addresses. */
data class LocalPlaylist(val id: String, val name: String, val tracks: List<BilibiliTrack> = emptyList())

class PlaylistState(initial: List<LocalPlaylist> = emptyList()) {
    var lists by mutableStateOf(initial.toList())
        private set

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun create(name: String): LocalPlaylist {
        val list = LocalPlaylist(Uuid.random().toString(), validName(name))
        lists = lists + list
        return list
    }

    fun rename(id: String, name: String) {
        val trimmed = validName(name)
        update(id) { it.copy(name = trimmed) }
    }

    fun delete(id: String) { lists = lists.filterNot { it.id == id } }
    fun add(id: String, tracks: List<BilibiliTrack>) = update(id) { it.copy(tracks = (it.tracks + tracks).distinctBy { track -> track.id }) }
    fun remove(id: String, trackId: String) = update(id) { it.copy(tracks = it.tracks.filterNot { track -> track.id == trackId }) }
    fun move(id: String, trackId: String, toIndex: Int) = update(id) { list ->
        val fromIndex = list.tracks.indexOfFirst { it.id == trackId }
        require(fromIndex >= 0 && toIndex in list.tracks.indices) { "Invalid playlist position" }
        list.copy(tracks = list.tracks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
    }

    private fun update(id: String, change: (LocalPlaylist) -> LocalPlaylist) {
        val index = lists.indexOfFirst { it.id == id }
        require(index >= 0) { "Playlist no longer exists" }
        lists = lists.toMutableList().apply { set(index, change(get(index))) }
    }

    private fun validName(name: String): String = name.trim().also { require(it.length in 1..40) { "Playlist name must contain 1 to 40 characters" } }
}
