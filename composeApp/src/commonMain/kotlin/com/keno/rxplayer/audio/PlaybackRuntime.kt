package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.persistence.LibraryStore
import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.AudioStream
import kotlinx.coroutines.*

/** Session-owned state; attaching or removing a screen does not own playback lifetime. */
class PlaybackRuntime(
    val playback: PlaybackState,
    initial: LibrarySnapshot,
    private val store: LibraryStore?,
    private val disposeResources: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    val favorites = mutableStateMapOf<String, ContentItem>().apply { putAll(initial.favorites.associateBy { it.stableKey }) }
    val recent = mutableStateListOf<BilibiliTrack>().apply { addAll(initial.recent) }
    var themeOverride by mutableStateOf(initial.dark)
    var followArtwork by mutableStateOf(initial.followArtwork)
    var message: PlaybackMessage? by mutableStateOf(null)
        private set
    private val job = SupervisorJob()
    val scope = CoroutineScope(job + dispatcher)
    private var closed = false
    private var warningShown = false
    private var messageId = 0L

    init {
        scope.launch {
            var lastAttempt: LibrarySnapshot? = null
            while (isActive) {
                playback.sync()?.let { select(it) }
                if (playback.phase == PlaybackPhase.Expired) scope.launch(start = CoroutineStart.UNDISPATCHED) { playback.recoverExpiredIfPlaying() }
                val playing = playback.track as? BilibiliTrack
                if (playing != null && playback.isPlaying && recent.firstOrNull()?.id != playing.id) {
                    recent.removeAll { it.id == playing.id }; recent.add(0, playing)
                    if (recent.size > 30) recent.removeAt(recent.lastIndex)
                }
                val snapshot = capture(periodic = true)
                if (snapshot != lastAttempt) { persist(snapshot); lastAttempt = snapshot }
                delay(100)
            }
        }
    }

    fun select(track: PlaybackTrack, autoPlay: Boolean = true, resumeAt: Int = 0) = scope.launch(start = CoroutineStart.UNDISPATCHED) { playback.select(track, autoPlay, resumeAt) }
    fun toggle() = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        if (playback.track == null) playback.queue.firstOrNull()?.let { playback.select(it) } else playback.activate()
    }
    fun play() = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        val track = playback.track
        when {
            playback.isPlaying || playback.isWaiting && playback.playWhenReady -> Unit
            track == null -> playback.queue.firstOrNull()?.let { playback.select(it) }
            playback.phase == PlaybackPhase.Expired -> playback.select(track, autoPlay = true, resumeAt = playback.positionMs, refreshing = true)
            else -> playback.activate()
        }
    }
    fun step(offset: Int) = scope.launch(start = CoroutineStart.UNDISPATCHED) { playback.step(offset) }
    fun seekToQueueItem(index: Int, positionMs: Int): Job? {
        val target = playback.queueInPlaybackOrder.getOrNull(index) ?: return null
        if (target.id == playback.track?.id && playback.canSeek) {
            playback.seek(positionMs)
            return null
        }
        return select(target, autoPlay = playback.playWhenReady, resumeAt = positionMs)
    }
    fun favorite(item: ContentItem) { if (favorites.remove(item.stableKey) == null) favorites[item.stableKey] = item }
    fun warn(text: String) { message = PlaybackMessage(++messageId, text) }
    private fun capture(periodic: Boolean = false) = LibrarySnapshot(favorites.values.toList(), recent.toList(), playback.checkpoint(periodic), themeOverride, followArtwork)
    private suspend fun persist(snapshot: LibrarySnapshot) {
        try { store?.save(snapshot) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (!warningShown) { warningShown = true; warn("本地数据保存失败，本次更改尚未保存") } }
    }
    suspend fun flush(pause: Boolean = false) { if (pause) playback.pause(); persist(capture()) }
    suspend fun close() {
        if (closed) return
        closed = true
        playback.pause()
        job.cancelAndJoin()
        try { withContext(NonCancellable) { persist(capture()) } } finally { disposeResources() }
    }
}

suspend fun openPlaybackRuntime(player: AudioPlayer, store: LibraryStore, resolver: suspend (BilibiliTrack) -> AudioStream,
    disposeResources: () -> Unit, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate): PlaybackRuntime {
    var writable = true
    val initial = try { store.load() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { writable = false; LibrarySnapshot() }
    return PlaybackRuntime(PlaybackState(player, resolver, initialQueue = emptyList(), restored = initial.playback), initial,
        if (writable) store else null, disposeResources, dispatcher).also {
        if (!writable) it.warn("本地数据读取失败，原文件已保留；当前更改不会写入")
    }
}

@Composable
expect fun rememberPlaybackRuntime(): PlaybackRuntime?
