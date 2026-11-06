package com.keno.rxplayer.persistence

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.MusicSource
import com.keno.rxplayer.audio.PlaybackCheckpoint
import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.VideoPart
import com.keno.rxplayer.library.LocalPlaylist
import com.keno.rxplayer.metadata.decodeSongRecord
import com.keno.rxplayer.metadata.encodeSongRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Short-lived connections, serialized writes, and one atomic snapshot transaction. */
class LibraryStore(private val path: String, private val prepareDirectory: () -> Unit = {}) {
    private val mutex = Mutex()

    suspend fun load(): LibrarySnapshot = access { c ->
        c.transaction {
            val items = linkedMapOf<String, ContentItem>()
            c.prepare("SELECT stable_key, source, content_id, title, author, artwork_url, duration_seconds FROM items").use { s ->
                while (s.step()) items[s.getText(0)] = ContentItem(MusicSource.valueOf(s.getText(1)), s.getText(2), s.getText(3), s.getText(4), s.nullableText(5), s.getInt(6))
            }
            val tracks = linkedMapOf<String, BilibiliTrack>()
            c.prepare("SELECT track_id, item_key, part_number, cid, title, duration_seconds, part_count FROM tracks").use { s ->
                while (s.step()) {
                    val item = items.getValue(s.getText(1))
                    require(item.source == MusicSource.Bilibili)
                    val track = BilibiliTrack(item, VideoPart(s.getInt(2), s.getLong(3), s.getText(4), s.getInt(5)), s.getInt(6))
                    check(track.id == s.getText(0)) { "Stored part identity does not match" }
                    tracks[track.id] = track
                }
            }
            val favorites = c.orderedKeys("favorites", "item_key").map { items.getValue(it) }
            val recent = c.orderedKeys("recent", "track_id").map { tracks.getValue(it) }
            val queue = c.orderedKeys("queue", "track_id").map { tracks.getValue(it) }
            val playOrder = c.orderedKeys("queue_play_order", "track_id")
            val members = linkedMapOf<String, MutableList<BilibiliTrack>>()
            c.prepare("SELECT playlist_id, track_id FROM playlist_tracks ORDER BY playlist_id, position").use { s ->
                while (s.step()) members.getOrPut(s.getText(0)) { mutableListOf() }.add(tracks.getValue(s.getText(1)))
            }
            val playlists = c.prepare("SELECT playlist_id, name FROM playlists ORDER BY position").use { s ->
                buildList { while (s.step()) add(LocalPlaylist(s.getText(0), s.getText(1), members[s.getText(0)]?.toList().orEmpty())) }
            }
            val songMetadata = c.prepare("SELECT track_id, payload FROM song_metadata ORDER BY track_id").use { s ->
                buildList { while (s.step()) add(decodeSongRecord(s.getText(0), s.getText(1))) }
            }
            c.prepare("SELECT dark, follow_artwork, shuffle, repeat_mode, volume, current_id, position_ms FROM app_state WHERE id = 1").use { s ->
                if (!s.step()) LibrarySnapshot(favorites, recent, PlaybackCheckpoint(queue, playOrder = playOrder), playlists = playlists, songMetadata = songMetadata)
                else LibrarySnapshot(favorites, recent, PlaybackCheckpoint(queue, s.nullableText(5), s.getInt(6), s.getInt(2) == 1, s.getInt(3) == 1, s.getDouble(4).toFloat(), playOrder), if (s.isNull(0)) null else s.getInt(0) == 1, s.getInt(1) == 1, playlists, songMetadata)
            }
        }
    }

    suspend fun save(snapshot: LibrarySnapshot): Unit = access { c ->
        val playback = snapshot.playback
        require(playback.queue.all { it is BilibiliTrack }) { "Only real Bilibili tracks are persisted" }
        require(playback.volume.isFinite())
        require(playback.playOrder.isEmpty() || playback.playOrder.size == playback.queue.size && playback.playOrder.toSet() == playback.queue.map { it.id }.toSet()) { "Playback order must contain the queue exactly once" }
        val tracks = (playback.queue.filterIsInstance<BilibiliTrack>() + snapshot.recent + snapshot.playlists.flatMap { it.tracks }).associateBy { it.id }
        val items = (snapshot.favorites + tracks.values.map { it.item }).associateBy { it.stableKey }
        c.transaction {
            // Delete dependents first. Any failed insert rolls the whole snapshot back.
            listOf("app_state", "song_metadata", "queue_play_order", "queue", "recent", "favorites", "playlist_tracks", "playlists", "tracks", "items").forEach { c.execSQL("DELETE FROM $it") }
            c.prepare("INSERT INTO items VALUES (?, ?, ?, ?, ?, ?, ?)").use { s ->
                items.values.forEach { item ->
                    s.bindText(1, item.stableKey); s.bindText(2, item.source.name); s.bindText(3, item.id)
                    s.bindText(4, item.title); s.bindText(5, item.author); s.bindNullableText(6, item.artworkUrl)
                    s.bindInt(7, item.durationSeconds); s.step(); s.reset(); s.clearBindings()
                }
            }
            c.prepare("INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?)").use { s ->
                tracks.values.forEach { track ->
                    s.bindText(1, track.id); s.bindText(2, track.item.stableKey); s.bindInt(3, track.part.number)
                    s.bindLong(4, track.part.cid); s.bindText(5, track.part.title); s.bindInt(6, track.part.durationSeconds)
                    s.bindInt(7, track.partCount); s.step(); s.reset(); s.clearBindings()
                }
            }
            c.writeOrder("favorites", snapshot.favorites.map { it.stableKey })
            c.writeOrder("recent", snapshot.recent.map { it.id })
            c.writeOrder("queue", playback.queue.map { it.id })
            c.writeOrder("queue_play_order", playback.playOrder)
            c.prepare("INSERT INTO song_metadata (track_id, payload) VALUES (?, ?)").use { s ->
                snapshot.songMetadata.forEach { record ->
                    s.bindText(1, record.trackId); s.bindText(2, encodeSongRecord(record))
                    s.step(); s.reset(); s.clearBindings()
                }
            }
            c.prepare("INSERT INTO playlists (playlist_id, position, name) VALUES (?, ?, ?)").use { s ->
                snapshot.playlists.forEachIndexed { index, playlist ->
                    s.bindText(1, playlist.id); s.bindInt(2, index); s.bindText(3, playlist.name)
                    s.step(); s.reset(); s.clearBindings()
                }
            }
            c.prepare("INSERT INTO playlist_tracks (playlist_id, position, track_id) VALUES (?, ?, ?)").use { s ->
                snapshot.playlists.forEach { playlist -> playlist.tracks.forEachIndexed { index, track ->
                    s.bindText(1, playlist.id); s.bindInt(2, index); s.bindText(3, track.id)
                    s.step(); s.reset(); s.clearBindings()
                } }
            }
            c.prepare("INSERT INTO app_state VALUES (1, ?, ?, ?, ?, ?, ?, ?)").use { s ->
                if (snapshot.dark == null) s.bindNull(1) else s.bindInt(1, if (snapshot.dark) 1 else 0)
                s.bindInt(2, if (snapshot.followArtwork) 1 else 0); s.bindInt(3, if (playback.shuffle) 1 else 0)
                s.bindInt(4, if (playback.repeat) 1 else 0); s.bindDouble(5, playback.volume.toDouble().coerceIn(0.0, 1.0))
                s.bindNullableText(6, playback.currentId?.takeIf { id -> playback.queue.any { it.id == id } })
                s.bindInt(7, playback.positionMs.coerceAtLeast(0)); s.step()
            }
        }
        Unit
    }

    private suspend fun <T> access(block: (SQLiteConnection) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            prepareDirectory()
            BundledSQLiteDriver().open(path).use { c ->
                c.execSQL("PRAGMA foreign_keys = ON")
                c.execSQL("PRAGMA busy_timeout = 3000")
                val version = c.prepare("PRAGMA user_version").use { s -> check(s.step()); s.getInt(0) }
                check(version in 0..4) { "Unsupported library database version: $version" }
                if (version == 0) c.transaction {
                    c.prepare("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { s ->
                        check(s.step() && s.getInt(0) == 0) { "Unversioned database is not empty" }
                    }
                    c.execSQL("CREATE TABLE items (stable_key TEXT PRIMARY KEY NOT NULL, source TEXT NOT NULL, content_id TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, artwork_url TEXT, duration_seconds INTEGER NOT NULL CHECK(duration_seconds >= 0))")
                    c.execSQL("CREATE TABLE tracks (track_id TEXT PRIMARY KEY NOT NULL, item_key TEXT NOT NULL REFERENCES items(stable_key), part_number INTEGER NOT NULL CHECK(part_number > 0), cid INTEGER NOT NULL CHECK(cid > 0), title TEXT NOT NULL, duration_seconds INTEGER NOT NULL CHECK(duration_seconds >= 0), part_count INTEGER NOT NULL CHECK(part_count > 0))")
                    c.execSQL("CREATE TABLE favorites (position INTEGER PRIMARY KEY, item_key TEXT NOT NULL UNIQUE REFERENCES items(stable_key))")
                    listOf("recent", "queue").forEach { c.execSQL("CREATE TABLE $it (position INTEGER PRIMARY KEY, track_id TEXT NOT NULL UNIQUE REFERENCES tracks(track_id))") }
                    c.execSQL("CREATE TABLE app_state (id INTEGER PRIMARY KEY CHECK(id = 1), dark INTEGER, follow_artwork INTEGER NOT NULL, shuffle INTEGER NOT NULL, repeat_mode INTEGER NOT NULL, volume REAL NOT NULL CHECK(volume BETWEEN 0 AND 1), current_id TEXT REFERENCES tracks(track_id), position_ms INTEGER NOT NULL CHECK(position_ms >= 0))")
                    c.execSQL("PRAGMA user_version = 1")
                }
                if (version <= 1) c.transaction {
                    c.execSQL("CREATE TABLE playlists (playlist_id TEXT PRIMARY KEY NOT NULL, position INTEGER NOT NULL UNIQUE, name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 40))")
                    c.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL REFERENCES playlists(playlist_id), position INTEGER NOT NULL, track_id TEXT NOT NULL REFERENCES tracks(track_id), PRIMARY KEY (playlist_id, track_id), UNIQUE (playlist_id, position))")
                    c.execSQL("PRAGMA user_version = 2")
                }
                if (version <= 2) c.transaction {
                    c.execSQL("CREATE TABLE queue_play_order (position INTEGER PRIMARY KEY, track_id TEXT NOT NULL UNIQUE REFERENCES tracks(track_id))")
                    c.execSQL("PRAGMA user_version = 3")
                }
                if (version <= 3) c.transaction {
                    c.execSQL("CREATE TABLE song_metadata (track_id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
                    c.execSQL("PRAGMA user_version = 4")
                }
                block(c)
            }
        }
    }
}

private fun SQLiteConnection.orderedKeys(table: String, column: String): List<String> =
    prepare("SELECT $column FROM $table ORDER BY position").use { s -> buildList { while (s.step()) add(s.getText(0)) } }

private fun SQLiteConnection.writeOrder(table: String, ids: List<String>) {
    prepare("INSERT INTO $table VALUES (?, ?)").use { s ->
        ids.forEachIndexed { index, id -> s.bindInt(1, index); s.bindText(2, id); s.step(); s.reset(); s.clearBindings() }
    }
}

private fun SQLiteStatement.nullableText(index: Int) = if (isNull(index)) null else getText(index)
private fun SQLiteStatement.bindNullableText(index: Int, value: String?) { if (value == null) bindNull(index) else bindText(index, value) }

private fun <T> SQLiteConnection.transaction(block: () -> T): T {
    execSQL("BEGIN IMMEDIATE TRANSACTION")
    try {
        val result = block()
        execSQL("COMMIT")
        return result
    } catch (failure: Throwable) {
        try { execSQL("ROLLBACK") } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
        throw failure
    }
}
