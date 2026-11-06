package com.keno.rxplayer.audio

import androidx.compose.runtime.*
import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.persistence.LibrarySnapshot
import com.keno.rxplayer.persistence.LibraryStore
import com.keno.rxplayer.source.ContentItem
import com.keno.rxplayer.source.AudioStream
import com.keno.rxplayer.source.BilibiliSource
import com.keno.rxplayer.source.BilibiliBrowseState
import com.keno.rxplayer.source.ContentAction
import com.keno.rxplayer.library.PlaylistState
import com.keno.rxplayer.library.LocalPlaylist
import com.keno.rxplayer.metadata.*
import kotlinx.coroutines.*

/** Session-owned state; attaching or removing a screen does not own playback lifetime. */
class PlaybackRuntime(
    val playback: PlaybackState,
    initial: LibrarySnapshot,
    private val store: LibraryStore?,
    private val disposeResources: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val browseSourceFactory: () -> BilibiliSource = { BilibiliSource() },
) {
    val favorites = mutableStateMapOf<String, ContentItem>().apply { putAll(initial.favorites.associateBy { it.stableKey }) }
    val recent = mutableStateListOf<BilibiliTrack>().apply { addAll(initial.recent) }
    val playlists = PlaylistState(initial.playlists)
    var playlistCandidate: BilibiliTrack? by mutableStateOf(null)
        private set
    var themeOverride by mutableStateOf(initial.dark)
    var followArtwork by mutableStateOf(initial.followArtwork)
    var message: PlaybackMessage? by mutableStateOf(null)
        private set
    private val job = SupervisorJob()
    val scope = CoroutineScope(job + dispatcher)
    private var metadataSource: SongMetadataSource? = null
    private var metadataVideoSource: BilibiliSource? = null
    private fun metadataSource() = metadataSource ?: SongMetadataSource().also { metadataSource = it }
    val songs = SongMetadataState(scope, initial.songMetadata,
        details = { track -> (metadataVideoSource ?: browseSourceFactory().also { metadataVideoSource = it }).details(com.keno.rxplayer.source.BilibiliReference(track.item.id, track.part.number)) },
        searchSongs = { metadataSource().search(it) }, loadSong = { metadataSource().load(it) }, notify = ::warn)
    private var closed = false
    private var warningShown = false
    private var messageId = 0L
    private var browsingSource: BilibiliSource? = null
    val browse: BilibiliBrowseState by lazy {
        BilibiliBrowseState(browseSourceFactory().also { browsingSource = it }, scope) { track, action ->
            when (action) {
                ContentAction.Play -> select(track)
                ContentAction.Enqueue -> playback.enqueue(track)
                ContentAction.Next -> playback.enqueue(track, nextInQueue = true)
                ContentAction.AddToPlaylist -> preparePlaylistCandidate(track)
                ContentAction.MatchSong -> songs.open(track)
            }
        }
    }

    init {
        scope.launch {
            var lastAttempt: LibrarySnapshot? = null
            while (isActive) {
                playback.sync()?.let { select(it) }
                if (playback.phase == PlaybackPhase.Expired) scope.launch(start = CoroutineStart.UNDISPATCHED) { playback.recoverExpiredIfPlaying() }
                val playing = playback.track as? BilibiliTrack
                songs.auto(playing)
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
    fun preparePlaylistCandidate(track: BilibiliTrack) { playlistCandidate = track }
    fun cancelPlaylistCandidate() { playlistCandidate = null }
    fun createPlaylist(name: String, addCandidate: Boolean = false): LocalPlaylist {
        val candidate = if (addCandidate) requireNotNull(playlistCandidate) else null
        val created = playlists.create(name)
        if (candidate != null) { playlists.add(created.id, listOf(candidate)); playlistCandidate = null }
        return playlists.lists.first { it.id == created.id }
    }
    fun addCandidateToPlaylist(id: String) {
        val candidate = playlistCandidate ?: return
        val list = playlists.lists.firstOrNull { it.id == id }
        if (list == null) { warn("歌单已删除，请重新选择"); return }
        val duplicate = list.tracks.any { it.id == candidate.id }
        playlists.add(id, listOf(candidate))
        playlistCandidate = null
        warn(if (duplicate) "此分 P 已在歌单中" else "已添加到「${list.name}」")
    }
    fun playPlaylist(id: String): Job? {
        val tracks = playlists.lists.firstOrNull { it.id == id }?.tracks ?: return null
        if (tracks.isEmpty()) { warn("歌单里还没有歌曲"); return null }
        return scope.launch(start = CoroutineStart.UNDISPATCHED) { playback.playAll(tracks) }
    }
    fun appendPlaylist(id: String) {
        val tracks = playlists.lists.firstOrNull { it.id == id }?.tracks ?: return
        if (tracks.isEmpty()) { warn("歌单里还没有歌曲"); return }
        val before = playback.queue.size
        playback.enqueueAll(tracks)
        warn(if (before == playback.queue.size) "歌单中的曲目已在队列中" else "已追加到播放队列")
    }
    fun warn(text: String) { message = PlaybackMessage(++messageId, text) }
    private fun capture(periodic: Boolean = false) = LibrarySnapshot(favorites.values.toList(), recent.toList(), playback.checkpoint(periodic), themeOverride, followArtwork, playlists.lists, songs.savedRecords)
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
        try { withContext(NonCancellable) { persist(capture()) } }
        finally { try { disposeResources() } finally { browsingSource?.close(); metadataSource?.close(); metadataVideoSource?.close() } }
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
