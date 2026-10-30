package com.keno.rxplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keno.rxplayer.audio.PlaybackCheckpoint
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.persistence.LibraryStore
import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.VideoPart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryPersistenceDeviceTest {
    @Test fun bundledDatabasePersistsAndReopensInPrivateStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "persistence-test-${System.nanoTime()}").apply { check(mkdirs()) }
        try {
            val path = File(directory, "test.db").absolutePath
            val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "本地数据库测试", "测试", null, 90)
            val first = BilibiliTrack(item, VideoPart(1, 101, "P1", 40), 2)
            val second = BilibiliTrack(item, VideoPart(2, 102, "P2", 50), 2)
            val saved = LibrarySnapshot(listOf(item), listOf(second, first), PlaybackCheckpoint(listOf(first, second), second.id, 3200, true, true, .3f), true, false)
            LibraryStore(path).save(saved)
            assertEquals(saved, LibraryStore(path).load())
            LibraryStore(path).save(LibrarySnapshot())
            assertEquals(LibrarySnapshot(), LibraryStore(path).load())
        } finally { directory.deleteRecursively() }
    }
}
