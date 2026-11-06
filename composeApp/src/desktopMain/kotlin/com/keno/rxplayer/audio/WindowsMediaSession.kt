package com.keno.rxplayer.audio

import androidx.compose.ui.awt.ComposeWindow
import com.keno.rxplayer.source.createSourceHttpClient
import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.*
import java.awt.Window
import java.util.concurrent.Executors

internal data class WindowsMediaState(val key: String, val title: String, val artist: String, val artwork: String?,
    val enabled: Boolean, val canSkip: Boolean, val status: Int, val position: Long, val duration: Long)

internal fun windowsMediaState(state: PlaybackState): WindowsMediaState {
    val track = state.track ?: state.queue.firstOrNull()
    val title = track?.title.orEmpty()
    val artist = track?.artist.orEmpty()
    val artwork = track?.artworkUrl
    val status = when (state.phase) {
        PlaybackPhase.Playing -> 3
        PlaybackPhase.Preparing, PlaybackPhase.Buffering, PlaybackPhase.Refreshing -> if (state.playWhenReady) 1 else 4
        PlaybackPhase.Idle, PlaybackPhase.Ended -> 2
        else -> 4
    }
    return WindowsMediaState(listOf(track?.id.orEmpty(), title, artist, artwork.orEmpty()).joinToString("\u0001"), title, artist, artwork,
        track != null, state.queue.size > 1, status,
        (if (state.isPlaying) state.positionMs / 1000 * 1000 else state.positionMs).toLong(),
        (if (state.track == null) track?.durationMs ?: 0 else state.durationMs).toLong())
}

internal fun dispatchWindowsMediaCommand(runtime: PlaybackRuntime, command: Int): Job? {
    val p = runtime.playback
    return when (command) {
        1 -> runtime.play()
        2 -> { p.pause(); null }
        3 -> { p.stop(); null }
        4, 5 -> {
            if (command == 5 && p.canSeek && p.positionMs > 3000) { p.seek(0); null }
            else if (p.queue.size < 2) null
            else {
                val order = p.queueInPlaybackOrder
                val index = order.indexOfFirst { it.id == p.track?.id }
                val target = if (index < 0) 0 else (index + if (command == 4) 1 else order.size - 1) % order.size
                runtime.seekToQueueItem(target, 0)
            }
        }
        else -> null
    }
}

internal interface WindowsMediaNative : Library {
    fun rx_create(window: Pointer, result: PointerByReference): Int
    fun rx_update(session: Pointer, key: WString, title: WString, artist: WString, enabled: Int, skip: Int, status: Int, position: Long, duration: Long): Int
    fun rx_cover(session: Pointer, key: WString, bytes: ByteArray, size: Int): Int
    fun rx_event(session: Pointer): Int
    fun rx_close(session: Pointer): Int
}

private object WindowsMediaLibrary {
    // JNA resolves the bundled win32-x86-64 DLL and handles its temporary-file cleanup.
    val api: WindowsMediaNative by lazy { Native.load("rxplayer-media", WindowsMediaNative::class.java) }
}

/** Native objects never leave their MTA worker; callbacks are applied to the UI-owned runtime. */
internal suspend fun runWindowsMediaSession(runtime: PlaybackRuntime) = coroutineScope {
    if (!System.getProperty("os.name").startsWith("Windows")) return@coroutineScope
    val worker = Executors.newSingleThreadExecutor { Thread(it, "RxPlayer-SMTC").apply { isDaemon = true } }.asCoroutineDispatcher()
    val http = createSourceHttpClient()
    var native: WindowsMediaNative? = null
    var session: Pointer? = null
    var cover: Job? = null
    try {
        val window = withTimeout(5000) {
            var found = Window.getWindows().filterIsInstance<ComposeWindow>().singleOrNull { it.isDisplayable }
            while (found == null) { delay(50); found = Window.getWindows().filterIsInstance<ComposeWindow>().singleOrNull { it.isDisplayable } }
            found
        }
        val hwnd = Native.getWindowPointer(window)
        native = withContext(worker) { WindowsMediaLibrary.api }
        val api = native
        withContext(worker) {
            val result = PointerByReference()
            check(api.rx_create(hwnd, result) >= 0) { "Unable to create Windows media session" }
            // Assign before returning across a cancellable dispatcher boundary.
            session = checkNotNull(result.value)
        }
        val handle = checkNotNull(session)
        println("RxPlayer: Windows media session connected")
        var previous: WindowsMediaState? = null
        while (isActive) {
            val state = windowsMediaState(runtime.playback)
            if (state != previous) {
                withContext(worker) {
                    check(api.rx_update(handle, WString(state.key), WString(state.title), WString(state.artist), if (state.enabled) 1 else 0,
                        if (state.canSkip) 1 else 0, state.status, state.position, state.duration) >= 0) { "Unable to update Windows media session" }
                }
                if (state.key != previous?.key) {
                    cover?.cancel()
                    cover = state.artwork?.let { url -> launch {
                        try {
                            val bytes = http.prepareGet(url).execute { response ->
                                check(response.status.value in 200..299)
                                response.bodyAsChannel().readRemaining(4L * 1024 * 1024 + 1).readByteArray().also { check(it.size <= 4 * 1024 * 1024) }
                            }
                            withContext(worker) { api.rx_cover(handle, WString(state.key), bytes, bytes.size) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Keep metadata with a blank cover when artwork is unavailable. */ }
                    } }
                }
                previous = state
            }
            val events = withContext(worker) { buildList { repeat(64) { val value = api.rx_event(handle); if (value != 0) add(value) } } }
            events.forEach { dispatchWindowsMediaCommand(runtime, it) }
            delay(250)
        }
    } catch (_: TimeoutCancellationException) { runtime.warn("Windows 系统媒体控制暂不可用，应用内播放不受影响") }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: LinkageError) { runtime.warn("Windows 系统媒体控制暂不可用，应用内播放不受影响") }
    catch (_: Exception) { runtime.warn("Windows 系统媒体控制暂不可用，应用内播放不受影响") }
    finally {
        withContext(NonCancellable) {
            cover?.cancelAndJoin()
            session?.let { handle -> withContext(worker) { native?.rx_close(handle); println("RxPlayer: Windows media session released") } }
        }
        http.close()
        worker.close()
    }
}
