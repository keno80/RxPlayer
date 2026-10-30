package com.keno.rxplayer.audio

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.source.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import kotlin.test.*
import java.nio.file.*
import java.time.Instant
import kotlinx.serialization.json.*

class BilibiliPlaybackLiveTest {
    @Test fun realPartsReachWindowsAudioEngine() {
        assumeTrue(System.getenv("RXPLAYER_PLAYBACK_PROBE") == "1")
        val report = linkedMapOf<String, JsonElement>("checked_at" to JsonPrimitive(Instant.now().toString()), "platform" to JsonPrimitive("Windows"))
        try { runBlocking {
            val source = BilibiliSource()
            val engine = DesktopAudioPlayer()
            try {
                val results = source.search("Blender"); assertTrue(results.items.isNotEmpty())
                val details = source.details(BilibiliReference("BV14u41147YH"))
                val first = BilibiliTrack(details.item, details.parts[0], details.parts.size)
                val second = BilibiliTrack(details.item, details.parts[1], details.parts.size)
                val state = PlaybackState(engine, { source.resolve(it.item.id, it.part.cid) }, emptyList())
                state.changeVolume(.05f)
                state.select(first)
                assertEquals(first.id, state.track?.id)
                assertTrue(state.durationMs > 0, "duration after source and engine preparation")
                withTimeout(20_000) { while (state.positionMs < 700) { state.sync(); delay(100) } }
                state.pause(); delay(300); state.sync()
                val paused = state.positionMs; delay(600); state.sync()
                assertTrue(kotlin.math.abs(state.positionMs - paused) < 150)
                state.seek(5000)
                withTimeout(15_000) { while (kotlin.math.abs(state.positionMs - 5000) > 500) { state.sync(); delay(100) } }
                state.enqueue(second, true); state.enqueue(second)
                assertEquals(2, state.queue.size)
                assertEquals(first.id, state.track?.id)
                state.step(1)
                assertEquals(second.id, state.track?.id)
                withTimeout(20_000) { while (state.positionMs < 700) { state.sync(); delay(100) } }
                report["search_count"] = JsonPrimitive(results.items.size)
                report["part_count"] = JsonPrimitive(details.parts.size)
                report["distinct_parts_played"] = JsonArray(listOf(first.part.cid, second.part.cid).map(::JsonPrimitive))
                report["pause_seek_queue_passed"] = JsonPrimitive(true)
                state.pause()
                val wav = silenceWav()
                engine.load(wav); engine.play()
                withTimeout(8_000) { while (!engine.isEnded) delay(100) }
                assertFalse(engine.isPlaying)
                report["natural_end_passed"] = JsonPrimitive(true)
                val children = ProcessHandle.current().descendants().use { all -> all.filter { it.info().command().orElse("").endsWith("mpv.exe", true) }.toList() }
                assertEquals(1, children.size)
                children.single().destroyForcibly()
                withTimeout(8_000) { while (engine.failure == null) delay(100) }
                engine.load(wav); assertTrue(engine.durationMs > 0)
                report["engine_restart_passed"] = JsonPrimitive(true)
                report["passed"] = JsonPrimitive(true)
            } finally { engine.release(); source.close(); delay(600) }
        } } catch (failure: Throwable) {
            report["passed"] = JsonPrimitive(false)
            report["failure"] = JsonPrimitive(if (failure is SourceFailure) failure.kind.name else failure::class.simpleName)
            throw AssertionError("Formal Windows playback probe failed: ${report["failure"]}")
        } finally {
            System.getenv("RXPLAYER_PLAYBACK_REPORT")?.let { path -> Path.of(path).also { Files.createDirectories(it.parent); Files.writeString(it, Json { prettyPrint = true }.encodeToString(JsonObject(report))) } }
        }
    }
    private fun silenceWav(): ByteArray {
        val size = 8000 * 2
        return java.nio.ByteBuffer.allocate(44 + size).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size); put(ByteArray(size))
        }.array()
    }
}
