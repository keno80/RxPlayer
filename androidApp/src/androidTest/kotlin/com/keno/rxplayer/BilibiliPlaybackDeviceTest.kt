package com.keno.rxplayer

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keno.rxplayer.audio.*
import com.keno.rxplayer.source.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BilibiliPlaybackDeviceTest {
    @Test fun realPartsReachMedia3() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runPlaybackProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject().put("platform", "Android").put("api", Build.VERSION.SDK_INT)
        try { runBlocking {
            val source = BilibiliSource()
            val engine = withContext(Dispatchers.Main) { createAndroidAudioPlayer(context) }
            try {
                val results = source.search("Blender"); assertTrue(results.items.isNotEmpty())
                val details = source.details(BilibiliReference("BV14u41147YH"))
                val first = BilibiliTrack(details.item, details.parts[0], details.parts.size)
                val second = BilibiliTrack(details.item, details.parts[1], details.parts.size)
                withContext(Dispatchers.Main) {
                    val state = PlaybackState(engine, { source.resolve(it.item.id, it.part.cid) }, emptyList())
                    state.changeVolume(.05f); state.select(first)
                    assertTrue(state.durationMs > 0)
                    withTimeout(20_000) { while (state.positionMs < 700) { state.sync(); delay(100) } }
                    state.pause(); delay(300); state.sync(); val paused = state.positionMs
                    delay(600); state.sync(); assertTrue(kotlin.math.abs(state.positionMs - paused) < 150)
                    state.seek(5000)
                    withTimeout(15_000) { while (kotlin.math.abs(state.positionMs - 5000) > 500) { state.sync(); delay(100) } }
                    state.enqueue(second, true); state.enqueue(second)
                    assertEquals(2, state.queue.size); assertEquals(first.id, state.track?.id)
                    state.step(1); assertEquals(second.id, state.track?.id)
                    withTimeout(20_000) { while (state.positionMs < 700) { state.sync(); delay(100) } }
                    report.put("distinct_parts_played", JSONArray(listOf(first.part.cid, second.part.cid))).put("pause_seek_queue_passed", true)
                }
                report.put("search_count", results.items.size).put("part_count", details.parts.size).put("passed", true)
            } finally { withContext(Dispatchers.Main) { engine.release() }; source.close() }
        } } catch (failure: Throwable) {
            val kind = if (failure is SourceFailure) failure.kind.name else failure.javaClass.simpleName
            report.put("passed", false).put("failure", kind).put("failure_frames", JSONArray(failure.stackTrace.take(12).map { "${it.className}.${it.methodName}:${it.lineNumber}" }))
            throw AssertionError("Formal Android playback probe failed: $kind")
        } finally { context.filesDir.resolve("bilibili-playback-probe.json").writeText(report.toString(2)) }
    }
}
