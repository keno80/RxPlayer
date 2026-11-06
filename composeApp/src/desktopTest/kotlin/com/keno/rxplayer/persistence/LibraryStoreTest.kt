package com.keno.rxplayer.persistence

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.keno.rxplayer.*
import com.keno.rxplayer.audio.PlaybackCheckpoint
import com.keno.rxplayer.library.LocalPlaylist
import com.keno.rxplayer.source.*
import com.keno.rxplayer.metadata.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class LibraryStoreTest {
    @Test fun versionFourKeepsOldMatchesAndPersistsProviderQualifiedSelections() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            val old = snapshot().copy(songMetadata = listOf(SongMetadataRecord(first.id, SongMatch(SongCandidate(42, "歌名", listOf("歌手"), "专辑", 40000)))))
            store.save(old)
            BundledSQLiteDriver().open(path).use { c ->
                c.execSQL("UPDATE song_metadata SET payload = '{\"disabled\":false,\"id\":42,\"title\":\"歌名\",\"artists\":[\"歌手\"],\"album\":\"专辑\",\"duration\":40000,\"lyrics\":[]}'")
                c.execSQL("PRAGMA user_version = 4")
            }
            assertEquals(old, LibraryStore(path).load())
            val selected = old.songMetadata.single().match!!.song.copy(provider = SongProvider.QQ, providerId = "QQMID42")
            val next = old.copy(songMetadata = listOf(SongMetadataRecord(first.id, SongMatch(selected))))
            store.save(next)
            assertEquals(next, LibraryStore(path).load())
            BundledSQLiteDriver().open(path).use { c -> c.prepare("PRAGMA user_version").use { s -> assertTrue(s.step()); assertEquals(5, s.getInt(0)) } }
        }
    }
    @Test fun versionThreeMigrationPreservesSourceAndRestoresMatchesAndOptOuts() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            BundledSQLiteDriver().open(path).use { c -> c.execSQL("DROP TABLE song_metadata"); c.execSQL("PRAGMA user_version = 3") }
            assertEquals(snapshot(), store.load())
            val metadata = listOf(SongMetadataRecord(first.id, SongMatch(SongCandidate(42, "歌名", listOf("歌手"), "专辑", 40000, "https://p1.music.126.net/a.jpg"), listOf(LyricLine(1000,"原创测试")), "网易云")), SongMetadataRecord(second.id, automaticDisabled = true))
            val expected = snapshot().copy(songMetadata = metadata)
            store.save(expected)
            assertEquals(expected, LibraryStore(path).load())
            assertEquals(item, store.load().favorites.single())
        }
    }
    @Test fun versionTwoMigrationKeepsTheLibraryAndPersistsTheExactShuffleRound() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            BundledSQLiteDriver().open(path).use { c ->
                c.execSQL("DROP TABLE queue_play_order")
                c.execSQL("DROP TABLE song_metadata")
                c.execSQL("PRAGMA user_version = 2")
            }
            assertEquals(snapshot(), LibraryStore(path).load())
            val saved = snapshot().copy(playback = snapshot().playback.copy(
                queue = listOf(second, first), playOrder = listOf(second.id, first.id)))
            store.save(saved)
            assertEquals(saved, LibraryStore(path).load())
            assertFails { store.save(saved.copy(playback = saved.playback.copy(playOrder = listOf(first.id, first.id)))) }
            assertEquals(saved, store.load())
        }
    }
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频标题", "作者", "https://i0.hdslb.com/cover.jpg", 90)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "第一P", 40), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "第二P", 50), 2)
    private fun snapshot() = LibrarySnapshot(listOf(item), listOf(second, first),
        PlaybackCheckpoint(listOf(first, second), second.id, 3200, true, true, .3f), dark = true, followArtwork = false)

    @Test fun playlistOnlyTracksSurviveReopenAndFailedSaveRollsBack() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            val expected = LibrarySnapshot(playlists = listOf(LocalPlaylist("one", "旅途", listOf(second, first)), LocalPlaylist("two", "旅途")))
            store.save(expected)
            assertEquals(expected, LibraryStore(path).load())
            BundledSQLiteDriver().open(path).use { c ->
                c.execSQL("CREATE TRIGGER fail_playlist BEFORE INSERT ON playlist_tracks BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            }
            assertFails { store.save(expected.copy(playlists = listOf(LocalPlaylist("one", "改名", listOf(first))))) }
            assertEquals(expected, store.load())
            store.save(LibrarySnapshot())
            assertEquals(LibrarySnapshot(), store.load())
        }
    }

    @Test fun versionOneMigrationPreservesExistingLibrary() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            // Recreate the actual v1 layout: v2 only adds these two tables.
            BundledSQLiteDriver().open(path).use { c ->
                c.execSQL("DROP TABLE playlist_tracks")
                c.execSQL("DROP TABLE playlists")
                c.execSQL("DROP TABLE queue_play_order")
                c.execSQL("DROP TABLE song_metadata")
                c.execSQL("PRAGMA user_version = 1")
            }
            assertEquals(snapshot(), LibraryStore(path).load())
            BundledSQLiteDriver().open(path).use { c ->
                c.prepare("PRAGMA user_version").use { s -> assertTrue(s.step()); assertEquals(5, s.getInt(0)) }
            }
            val upgraded = snapshot().copy(playlists = listOf(LocalPlaylist("one", "保留分P", listOf(second, first))))
            store.save(upgraded)
            assertEquals(upgraded, LibraryStore(path).load())
        }
    }

    @Test fun diskReopenRestoresExactPartsOrderAndSettings() = runTest {
        withDatabase { path ->
            assertEquals(LibrarySnapshot(), LibraryStore(path).load())
            LibraryStore(path).save(snapshot())
            assertEquals(snapshot(), LibraryStore(path).load())
        }
    }

    @Test fun emptySaveReallyRemovesFavoritesHistoryAndQueue() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            store.save(LibrarySnapshot())
            assertEquals(LibrarySnapshot(), LibraryStore(path).load())
        }
    }

    @Test fun transactionFailurePreservesPreviousSnapshot() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            val connection = BundledSQLiteDriver().open(path)
            connection.execSQL("CREATE TRIGGER fail_queue BEFORE INSERT ON queue BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            connection.close()
            assertFails { store.save(snapshot().copy(favorites = emptyList())) }
            assertEquals(snapshot(), store.load())
        }
    }

    @Test fun newerDatabaseIsNotDowngradedOrOverwritten() = runTest {
        withDatabase { path ->
            val store = LibraryStore(path)
            store.save(snapshot())
            BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version = 999") }
            assertFails { store.load() }
            assertFails { store.save(LibrarySnapshot()) }
            BundledSQLiteDriver().open(path).use { c ->
                c.prepare("SELECT COUNT(*) FROM queue").use { s -> assertTrue(s.step()); assertEquals(2, s.getInt(0)) }
                c.prepare("PRAGMA user_version").use { s -> assertTrue(s.step()); assertEquals(999, s.getInt(0)) }
            }
        }
    }

    @Test fun unversionedExistingDataIsPreserved() = runTest {
        withDatabase { path ->
            BundledSQLiteDriver().open(path).use { c -> c.execSQL("CREATE TABLE existing_data (value TEXT)"); c.execSQL("INSERT INTO existing_data VALUES ('keep')") }
            assertFails { LibraryStore(path).load() }
            assertFails { LibraryStore(path).save(LibrarySnapshot()) }
            BundledSQLiteDriver().open(path).use { c ->
                c.prepare("SELECT value FROM existing_data").use { s -> assertTrue(s.step()); assertEquals("keep", s.getText(0)) }
            }
        }
    }

    private suspend fun withDatabase(block: suspend (String) -> Unit) {
        val directory = Files.createTempDirectory("rxplayer-library-test").toFile()
        try { block(directory.resolve("library.db").absolutePath) } finally { directory.deleteRecursively() }
    }
}
