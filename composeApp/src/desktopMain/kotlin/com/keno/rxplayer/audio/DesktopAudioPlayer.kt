package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.source.AudioStream
import com.sun.jna.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private interface PipeApi : StdCallLibrary {
    fun CreateFileW(name: WString, access: Int, share: Int, security: Pointer?, creation: Int, flags: Int, template: Pointer?): Pointer?
    fun PeekNamedPipe(handle: Pointer, buffer: Pointer?, size: Int, read: IntByReference?, available: IntByReference, left: IntByReference?): Boolean
    fun ReadFile(handle: Pointer, bytes: ByteArray, size: Int, read: IntByReference, overlapped: Pointer?): Boolean
    fun WriteFile(handle: Pointer, bytes: ByteArray, size: Int, written: IntByReference, overlapped: Pointer?): Boolean
    fun CloseHandle(handle: Pointer): Boolean
}

/** One actor owns both directions of the synchronous pipe. UI controls never block on IPC. */
class DesktopAudioPlayer : AudioPlayer {
    private data class Snapshot(val duration: Int = 0, val position: Int = 0, val playing: Boolean = false, val buffering: Boolean = false, val ended: Boolean = false, val failure: PlaybackFailure? = null)
    private data class SeekRequest(val position: Int, val submitted: Boolean = false)
    private val pendingSeek = AtomicReference<SeekRequest?>(null)
    @Volatile private var snapshot = Snapshot()
    override val durationMs get() = snapshot.duration
    override val positionMs get() = pendingSeek.get()?.position ?: snapshot.position
    override val isPlaying get() = snapshot.playing
    override val isBuffering get() = snapshot.buffering || pendingSeek.get() != null
    override val isEnded get() = pendingSeek.get() == null && snapshot.ended
    override val failure get() = snapshot.failure
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val requests = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val generation = AtomicLong()
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<CompletableDeferred<Unit>>()
    private var pipe: Pipe? = null
    private var temporary: File? = null
    @Volatile private var closed = false
    private var deadline: Long? = null
    private val worker = scope.launch {
        try {
            while (!closed) {
                val command = withTimeoutOrNull(100) { requests.receive() }
                try { command?.invoke() } catch (_: Exception) { pendingSeek.set(null); snapshot = snapshot.copy(playing = false, failure = PlaybackFailure()) }
                pipe?.let { ipc ->
                    try {
                        val duration = ipc.get("duration")?.jsonPrimitive?.doubleOrNull?.times(1000)?.toInt() ?: snapshot.duration
                        val seek = pendingSeek.get()
                        val seekFinished = seek?.submitted == true && ipc.get("seeking")?.jsonPrimitive?.booleanOrNull == false
                        val position = ipc.get("time-pos")?.jsonPrimitive?.doubleOrNull?.times(1000)?.toInt() ?: snapshot.position
                        val paused = ipc.get("pause")?.jsonPrimitive?.booleanOrNull ?: true
                        val buffering = ipc.get("paused-for-cache")?.jsonPrimitive?.booleanOrNull ?: false
                        val ended = ipc.ended || ipc.get("eof-reached")?.jsonPrimitive?.booleanOrNull == true
                        snapshot = snapshot.copy(duration = duration, position = position, playing = !paused && !buffering && !ended, buffering = buffering, ended = ended,
                            failure = if (ipc.failed) PlaybackFailure(if (deadline?.let { System.currentTimeMillis() / 1000 >= it } == true) PlaybackPhase.Expired else PlaybackPhase.Failed) else snapshot.failure)
                        if (snapshot.failure != null) pendingSeek.set(null)
                        else if (seekFinished) pendingSeek.compareAndSet(seek, null)
                    } catch (_: Exception) { pendingSeek.set(null); invalidatePipe(); snapshot = snapshot.copy(playing = false, failure = PlaybackFailure()) }
                }
            }
        } finally { pending.forEach { it.cancel() }; invalidatePipe(); temporary?.delete(); requests.close() }
    }
    override suspend fun load(bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) { File.createTempFile("rxplayer-", ".wav").apply { writeBytes(bytes) } }
        try { loadInput(file.absolutePath, emptyMap(), null, file) } catch (error: Exception) { file.delete(); throw error }
    }
    override suspend fun load(stream: AudioStream) = loadInput(stream.url, stream.headers, stream.expiresAtEpochSeconds, null)
    private suspend fun loadInput(uri: String, headers: Map<String, String>, expires: Long?, file: File?) {
        check(!closed)
        pendingSeek.set(null)
        val token = generation.incrementAndGet()
        val result = CompletableDeferred<Unit>()
        pending.add(result)
        result.invokeOnCompletion { error -> pending.remove(result); if (error != null) file?.delete() }
        requests.send {
            if (token != generation.get() || !result.isActive) { file?.delete(); result.cancel(); return@send }
            try {
                val ipc = pipe ?: Pipe().also { pipe = it }
                ipc.command("stop")
                temporary?.delete(); temporary = file; deadline = expires
                snapshot = Snapshot(buffering = true)
                ipc.failed = false; ipc.loaded = false; ipc.ended = false
                ipc.command("set_property", "pause", true)
                ipc.command("set_property", "http-header-fields", headers.map { "${it.key}: ${it.value}" })
                ipc.command("loadfile", uri, "replace")
                val until = System.nanoTime() + 30_000_000_000L
                while (!ipc.loaded && !ipc.failed && token == generation.get() && result.isActive && System.nanoTime() < until) {
                    ipc.drain(); delay(10)
                }
                if (token != generation.get() || !result.isActive) { ipc.command("stop"); result.cancel(); return@send }
                if (!ipc.loaded || ipc.failed) throw PlaybackExceptionSafe(PlaybackFailure(if (expires?.let { System.currentTimeMillis() / 1000 >= it } == true) PlaybackPhase.Expired else PlaybackPhase.Failed))
                val duration = ipc.get("duration")?.jsonPrimitive?.doubleOrNull?.times(1000)?.toInt() ?: 0
                snapshot = Snapshot(duration = duration)
                result.complete(Unit)
            } catch (error: Exception) { invalidatePipe(); snapshot = Snapshot(failure = (error as? PlaybackExceptionSafe)?.failure ?: PlaybackFailure()); result.completeExceptionally(PlaybackExceptionSafe(snapshot.failure!!)) }
        }
        try { result.await() } catch (cancelled: CancellationException) { generation.compareAndSet(token, token + 1); result.cancel(); throw cancelled }
    }
    private fun control(vararg command: Any) { if (!closed) requests.trySend { pipe?.command(*command); Unit } }
    override fun play() = control("set_property", "pause", false)
    override fun pause() = control("set_property", "pause", true)
    override fun seekTo(positionMs: Int) {
        if (closed) return
        val request = SeekRequest(positionMs)
        pendingSeek.set(request)
        snapshot = snapshot.copy(ended = false)
        requests.trySend {
            pipe?.let { ipc ->
                ipc.ended = false
                check(ipc.command("seek", positionMs / 1000.0, "absolute+exact")["error"]?.jsonPrimitive?.content == "success")
                // Keep the requested position visible until the actor observes seek completion.
                pendingSeek.compareAndSet(request, request.copy(submitted = true))
            }
            Unit
        }
    }
    override fun setVolume(volume: Float) = control("set_property", "volume", volume.coerceIn(0f, 1f) * 100)
    override fun release() { closed = true; generation.incrementAndGet(); pending.forEach { it.cancel() } }
    private fun invalidatePipe() { runCatching { pipe?.close() }; pipe = null }

    private class Pipe : AutoCloseable {
        private val api = Native.load("kernel32", PipeApi::class.java)
        private val name = "\\\\.\\pipe\\rxplayer-${UUID.randomUUID()}"
        private val process: Process
        private val handle: Pointer
        private val buffer = ByteArrayOutputStream()
        private val lines = ArrayDeque<JsonObject>()
        private var sequence = 0
        var loaded = false
        var failed = false
        var ended = false
        init {
            val executable = System.getProperty("rxplayer.mpv.path") ?: System.getenv("RXPLAYER_MPV_PATH") ?: error("Validated desktop engine not configured")
            require(File(executable).isFile)
            process = ProcessBuilder(executable, "--no-config", "--load-scripts=no", "--terminal=no", "--no-video", "--idle=yes", "--ytdl=no", "--ao=wasapi", "--input-ipc-server=$name")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            var connected: Pointer? = null
            val until = System.nanoTime() + 5_000_000_000L
            while (connected == null && process.isAlive && System.nanoTime() < until) {
                val value = api.CreateFileW(WString(name), 0xC0000000.toInt(), 0, null, 3, 0, null)
                if (value != null && Pointer.nativeValue(value) != -1L) connected = value else Thread.sleep(20)
            }
            if (connected == null) { process.destroyForcibly(); error("Engine IPC unavailable") }
            handle = connected
        }
        private fun element(value: Any?): JsonElement = when(value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map(::element))
            else -> JsonPrimitive(value.toString())
        }
        fun command(vararg values: Any): JsonObject {
            val id = ++sequence
            val bytes = buildJsonObject { put("command", JsonArray(values.map(::element))); put("request_id", id) }.toString().plus("\n").toByteArray()
            val written = IntByReference()
            check(api.WriteFile(handle, bytes, bytes.size, written, null) && written.value == bytes.size)
            val until = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < until && process.isAlive) {
                readAvailable()
                while (lines.isNotEmpty()) {
                    val line = lines.removeFirst(); event(line)
                    if (line["request_id"]?.jsonPrimitive?.intOrNull == id) return line
                }
                Thread.sleep(5)
            }
            error("Engine IPC timeout")
        }
        fun get(property: String) = command("get_property", property)["data"]?.takeIf { it != JsonNull }
        fun drain() { readAvailable(); while (lines.isNotEmpty()) event(lines.removeFirst()) }
        private fun event(line: JsonObject) {
            when (line["event"]?.jsonPrimitive?.contentOrNull) {
                "file-loaded" -> loaded = true
                "end-file" -> when (line["reason"]?.jsonPrimitive?.contentOrNull) { "error" -> failed = true; "eof" -> ended = true }
            }
        }
        private fun readAvailable() {
            val available = IntByReference()
            check(api.PeekNamedPipe(handle, null, 0, null, available, null))
            if (available.value <= 0) return
            val bytes = ByteArray(available.value.coerceAtMost(65536)); val count = IntByReference()
            check(api.ReadFile(handle, bytes, bytes.size, count, null))
            for (byte in bytes.take(count.value)) {
                if (byte == 10.toByte()) {
                    val line = buffer.toByteArray().toString(Charsets.UTF_8); buffer.reset()
                    runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()?.let(lines::addLast)
                } else { check(buffer.size() < 1024 * 1024); buffer.write(byte.toInt()) }
            }
        }
        override fun close() {
            runCatching { command("quit") }
            api.CloseHandle(handle)
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}
