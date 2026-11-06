package com.keno.rxplayer.metadata

import androidx.compose.runtime.*
import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.source.VideoDetails
import kotlinx.coroutines.*

/** Enrichment owns no audio player and never changes a source identity or playback position. */
class SongMetadataState(
    private val scope: CoroutineScope,
    initial: List<SongMetadataRecord> = emptyList(),
    private val details: suspend (BilibiliTrack) -> VideoDetails,
    private val searchSongs: suspend (String) -> List<SongCandidate>,
    private val loadSong: suspend (SongCandidate) -> SongMatch,
    private val notify: (String) -> Unit = {},
) {
    private val records = mutableStateMapOf<String, SongMetadataRecord>().apply { initial.forEach { put(it.trackId, it) } }
    val savedRecords get() = records.values.sortedBy { it.trackId }
    private val descriptions = mutableMapOf<String, String>()
    private val attempted = mutableSetOf<String>()
    private var activeId: String? = null
    private var automaticJob: Job? = null
    private var automaticTicket = 0L
    private var requestJob: Job? = null
    private var requestTicket = 0L
    var target: BilibiliTrack? by mutableStateOf(null)
        private set
    var query by mutableStateOf("")
    var candidates by mutableStateOf(emptyList<SongCandidate>())
        private set
    var searching by mutableStateOf(false)
        private set
    var applying by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set

    fun display(track: PlaybackTrack): PlaybackTrack = if (track is BilibiliTrack) track.copy(songMetadata = records[track.id]?.match) else track
    fun selected(trackId: String) = records[trackId]?.match

    fun auto(track: BilibiliTrack?): Job? {
        if (activeId != track?.id) { ++automaticTicket; automaticJob?.cancel(); activeId = track?.id }
        if (track == null || track.id in records || target?.id == track.id || !attempted.add(track.id)) return null
        // Avoid speculative network queries for arbitrary videos without any song-shaped title.
        val title = if (track.partCount > 1) track.part.title else track.item.title
        if (songHint(track) == null && !Regex("[《〈「]").containsMatchIn(title)) return null
        val ticket = ++automaticTicket
        return scope.launch {
            try {
                val description = readDescription(track) ?: return@launch
                val hint = songHint(track, description) ?: return@launch
                val candidate = autoMatch(hint, track, searchSongs("${hint.title} ${hint.artist}")) ?: return@launch
                val match = loadSong(candidate); ensureActive()
                if (autoMatch(hint, track, listOf(match.song))?.id != candidate.id) return@launch
                if (ticket == automaticTicket && activeId == track.id && track.id !in records) {
                    records[track.id] = SongMetadataRecord(track.id, compatible(track, match))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Optional metadata never interrupts playback or emits background toasts. */ }
        }.also { automaticJob = it }
    }

    fun open(track: BilibiliTrack) {
        if (activeId == track.id) { ++automaticTicket; automaticJob?.cancel() }
        close()
        target = track.copy(songMetadata = null)
        val hint = songHint(track, descriptions[track.id].orEmpty())
        query = if (hint != null) "${hint.title} ${hint.artist}" else (if (track.partCount > 1) track.part.title else track.item.title).take(200)
        search()
    }

    fun search(): Job? {
        val track = target ?: return null
        val term = query.trim()
        if (term.isEmpty() || term.length > 200) { notify("请输入 1～200 字的歌名或歌手"); return null }
        requestJob?.cancel()
        val ticket = ++requestTicket
        searching = true; applying = false; searched = false; candidates = emptyList()
        return scope.launch {
            try {
                val result = searchSongs(term); ensureActive()
                if (ticket == requestTicket && target?.id == track.id) candidates = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == requestTicket) notify("暂时无法获取歌曲信息，请稍后重试") }
            finally { if (ticket == requestTicket) { searching = false; searched = true } }
        }.also { requestJob = it }
    }

    fun apply(candidate: SongCandidate): Job? {
        val track = target ?: return null
        if (searching || applying || candidates.none { it.id == candidate.id }) return null
        val ticket = ++requestTicket
        applying = true
        return scope.launch {
            try {
                if (track.id !in descriptions) readDescription(track)
                val match = loadSong(candidate); ensureActive()
                if (ticket == requestTicket && target?.id == track.id) {
                    records[track.id] = SongMetadataRecord(track.id, compatible(track, match), automaticDisabled = true)
                    close(); notify("已更新歌曲信息，播放与队列保持不变")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == requestTicket) notify("暂时无法获取歌曲信息，请稍后重试") }
            finally { if (ticket == requestTicket) applying = false }
        }.also { requestJob = it }
    }

    private suspend fun readDescription(track: BilibiliTrack): String? = try {
        val info = details(track); currentCoroutineContext().ensureActive()
        if (info.item.id != track.item.id || info.parts.none { it.cid == track.part.cid }) null
        else info.description.also { descriptions[track.id] = it }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    private fun compatible(track: BilibiliTrack, match: SongMatch): SongMatch =
        if (track.id in descriptions && lyricsFit(track, match.song, descriptions[track.id].orEmpty())) match else match.copy(lyrics = emptyList(), lyricsSource = null)

    fun restoreOriginal() {
        val track = target ?: return
        if (activeId == track.id) { ++automaticTicket; automaticJob?.cancel() }
        records[track.id] = SongMetadataRecord(track.id, automaticDisabled = true)
        close(); notify("已恢复视频封面和原信息")
    }
    fun close() {
        ++requestTicket; requestJob?.cancel(); requestJob = null
        target = null; searching = false; applying = false; searched = false; candidates = emptyList()
    }
}
