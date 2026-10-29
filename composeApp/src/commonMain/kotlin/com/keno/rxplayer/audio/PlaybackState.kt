package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.demoTracks
import com.keno.rxplayer.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

enum class PlaybackPhase { Idle, Preparing, Buffering, Playing, Paused, Refreshing, Failed, Unavailable, Expired, Ended }

data class PlaybackFailure(val phase: PlaybackPhase = PlaybackPhase.Failed)
data class PlaybackMessage(val id: Long, val text: String)

@OptIn(ExperimentalResourceApi::class)
class PlaybackState(
    private val player: AudioPlayer,
    private val readAudio: suspend (DemoTrack) -> ByteArray = { Res.readBytes(it.resourcePath) },
) {
    private val selectionMutex = Mutex()
    private var generation = 0L
    private var loadingJob: Job? = null
    private var messageId = 0L
    private var prepared = false
    private var shuffleOrder = emptyList<String>()
    var track: DemoTrack? by mutableStateOf(null)
        private set
    var phase by mutableStateOf(PlaybackPhase.Idle)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var queue by mutableStateOf(demoTracks)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(false)
        private set
    var volume by mutableFloatStateOf(.65f)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    var message: PlaybackMessage? by mutableStateOf(null)
        private set

    val isPlaying get() = phase == PlaybackPhase.Playing
    val isLoading get() = phase == PlaybackPhase.Preparing || phase == PlaybackPhase.Refreshing
    val isWaiting get() = isLoading || phase == PlaybackPhase.Buffering
    val canSeek get() = prepared && durationMs > 0 && (phase == PlaybackPhase.Playing || phase == PlaybackPhase.Paused)
    val canChangeVolume get() = prepared && player.supportsVolume

    suspend fun select(next: DemoTrack, autoPlay: Boolean = true, resumeAt: Int = 0, refreshing: Boolean = false) {
        val job = currentCoroutineContext()[Job]
        if (loadingJob !== job) loadingJob?.cancel()
        val ticket = ++generation
        loadingJob = job
        if (track?.id != next.id) durationMs = 0
        track = next
        if (queue.none { it.id == next.id }) {
            queue = queue + next
            if (shuffle) shuffleOrder = shuffleOrder + next.id
        }
        positionMs = resumeAt
        playWhenReady = autoPlay
        message = null
        prepared = false
        phase = if (refreshing) PlaybackPhase.Refreshing else PlaybackPhase.Preparing
        try {
            selectionMutex.withLock {
                currentCoroutineContext().ensureActive()
                player.pause()
                val bytes = readAudio(next)
                currentCoroutineContext().ensureActive()
                player.load(bytes)
                currentCoroutineContext().ensureActive()
                if (ticket != generation) return@withLock
                check(player.durationMs > 0) { "Audio duration unavailable" }
                durationMs = player.durationMs
                positionMs = resumeAt.coerceIn(0, durationMs)
                player.seekTo(positionMs)
                player.setVolume(volume)
                prepared = true
                if (playWhenReady) player.play()
                phase = if (player.isPlaying) PlaybackPhase.Playing else PlaybackPhase.Paused
                sync()
            }
        } catch (cancelled: CancellationException) {
            if (ticket == generation) {
                playWhenReady = false
                phase = if (refreshing) PlaybackPhase.Expired else PlaybackPhase.Paused
            }
            throw cancelled
        } catch (_: Exception) {
            if (ticket == generation) reportFailure(PlaybackFailure())
        } finally {
            if (ticket == generation) loadingJob = null
        }
    }

    suspend fun activate() {
        val selected = track ?: return
        when {
            isLoading -> cancelPreparation()
            phase == PlaybackPhase.Buffering -> pause()
            phase == PlaybackPhase.Unavailable -> notify("当前内容不可播放，可换曲或查看音源账号")
            phase == PlaybackPhase.Expired -> select(selected, playWhenReady, positionMs, refreshing = true)
            phase == PlaybackPhase.Failed -> select(selected, resumeAt = positionMs)
            phase == PlaybackPhase.Ended -> select(selected)
            !prepared -> select(selected, resumeAt = positionMs)
            else -> {
                try {
                    playWhenReady = !isPlaying
                    if (playWhenReady) player.play() else player.pause()
                    sync()
                } catch (_: Exception) { reportFailure(PlaybackFailure()) }
            }
        }
    }

    fun seek(position: Int) {
        if (!canSeek) return
        try {
            player.seekTo(position.coerceIn(0, durationMs))
            positionMs = player.positionMs.coerceIn(0, durationMs)
        } catch (_: Exception) { reportFailure(PlaybackFailure()) }
    }

    fun cancelPreparation() {
        val refreshing = phase == PlaybackPhase.Refreshing
        ++generation
        loadingJob?.cancel()
        loadingJob = null
        playWhenReady = false
        try { player.pause() } catch (_: Exception) { player.release() }
        phase = if (refreshing) PlaybackPhase.Expired else PlaybackPhase.Paused
    }

    fun pause() {
        if (isLoading) { cancelPreparation(); return }
        playWhenReady = false
        try {
            player.pause()
            if (prepared && phase !in faultPhases && phase != PlaybackPhase.Ended) phase = PlaybackPhase.Paused
        } catch (_: Exception) { reportFailure(PlaybackFailure()) }
    }

    suspend fun step(offset: Int) { adjacent(offset)?.let { select(it) } }

    private fun adjacent(offset: Int): DemoTrack? {
        val order = playOrder()
        if (order.isEmpty()) return null
        val index = order.indexOf(track?.id)
        val id = order[(index.coerceAtLeast(0) + offset + order.size) % order.size]
        return queue.firstOrNull { it.id == id }
    }

    private fun playOrder() = if (shuffle) shuffleOrder else queue.map { it.id }

    fun removeFromQueue(id: String) {
        val index = queue.indexOfFirst { it.id == id }
        queue = queue.filterNot { it.id == id }
        shuffleOrder = shuffleOrder.filterNot { it == id }
        if (id == track?.id) {
            cancelPreparation()
            prepared = false
            durationMs = 0
            positionMs = 0
            track = queue.getOrNull(index.coerceIn(0, queue.lastIndex.coerceAtLeast(0))) ?: track
            phase = PlaybackPhase.Paused
        }
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        if (shuffle) {
            val selected = queue.firstOrNull { it.id == track?.id }
            shuffleOrder = listOfNotNull(selected?.id) + queue.filterNot { it.id == selected?.id }.shuffled().map { it.id }
        }
    }
    fun toggleRepeat() { repeat = !repeat }
    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        if (prepared) try { player.setVolume(volume) } catch (_: Exception) { notify("当前设备无法调整音量") }
    }

    /** Returns a next selection once, only after the engine reports completion. */
    fun sync(): DemoTrack? {
        if (isLoading || !prepared || phase == PlaybackPhase.Ended || phase in faultPhases) return null
        try {
            player.failure?.let { reportFailure(it); return null }
            positionMs = player.positionMs.coerceIn(0, durationMs)
            if (player.isEnded && playWhenReady) {
                playWhenReady = false
                phase = PlaybackPhase.Ended
                val order = playOrder()
                val index = order.indexOf(track?.id)
                return if (repeat || index >= 0 && index < order.lastIndex) adjacent(1) else null
            }
            phase = when {
                player.isBuffering && playWhenReady -> PlaybackPhase.Buffering
                player.isPlaying -> PlaybackPhase.Playing
                else -> PlaybackPhase.Paused
            }
        } catch (_: Exception) { reportFailure(PlaybackFailure()) }
        return null
    }

    private fun reportFailure(failure: PlaybackFailure) {
        // An engine failure must not leave the previous audio running underneath the error UI.
        try { player.pause() } catch (_: Exception) { /* The failed device may already be closed. */ }
        phase = failure.phase.takeIf { it in faultPhases } ?: PlaybackPhase.Failed
        notify(when (phase) {
            PlaybackPhase.Unavailable -> "当前内容不可播放，可换曲或查看音源账号"
            PlaybackPhase.Expired -> "播放地址已失效，点击播放按钮重新获取"
            else -> "播放失败，点击播放按钮重试"
        })
    }

    private fun notify(text: String) { message = PlaybackMessage(++messageId, text) }

    private companion object {
        val faultPhases = setOf(PlaybackPhase.Failed, PlaybackPhase.Unavailable, PlaybackPhase.Expired)
    }
}
