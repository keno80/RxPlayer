package com.keno.rxplayer.persistence

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.keno.rxplayer.*
import com.keno.rxplayer.audio.PlaybackCheckpoint
import com.keno.rxplayer.source.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class LibraryStoreTest {
    private val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "视频标题", "作者", "https://i0.hdslb.com/cover.jpg", 90)
    private val first = BilibiliTrack(item, VideoPart(1, 101, "第一P", 40), 2)
    private val second = BilibiliTrack(item, VideoPart(2, 102, "第二P", 50), 2)
    private fun snapshot() = LibrarySnapshot(listOf(item), listOf(second, first),
        PlaybackCheckpoint(listOf(first, second), second.id, 3200, true, true, .3f), dark = true, followArtwork = false)

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
