package com.keno.rxplayer.source

import androidx.compose.runtime.*
import com.keno.rxplayer.BilibiliTrack
import kotlinx.coroutines.*

enum class ContentAction { Play, Enqueue, Next }
data class PartSelection(val details: VideoDetails, val action: ContentAction)

/** Browsing never mutates playback until a single part has been confirmed. */
class BilibiliBrowseState(
    private val source: BilibiliSource, private val scope: CoroutineScope,
    private val onAction: (BilibiliTrack, ContentAction) -> Unit,
) {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf(emptyList<ContentItem>())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf<PartSelection?>(null)
        private set
    var readingDetails by mutableStateOf(false)
        private set
    var feedback by mutableStateOf<Pair<Long, String>?>(null)
        private set
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var searchGeneration = 0
    private var detailsGeneration = 0
    private var page = 0
    private var hasMore = false
    private var feedbackId = 0L
    val partCounts = mutableStateMapOf<String, Int>()

    fun submit(): Job? {
        val value = query.trim()
        if (value.isEmpty()) return null
        searchJob?.cancel(); cancelSelection()
        submitted = value; results = emptyList(); page = 0; hasMore = false; error = null
        val ticket = ++searchGeneration
        searchJob = scope.launch {
            searching = true
            try {
                val reference = runCatching { BilibiliReference.parse(value) }.getOrNull()
                if (reference != null) {
                    val details = source.details(reference)
                    ensureActive()
                    if (ticket == searchGeneration) { results = listOf(details.item); partCounts[details.item.id] = details.parts.size }
                } else {
                    require(!value.contains("://") && value.length <= 200)
                    val response = source.search(value)
                    ensureActive()
                    if (ticket == searchGeneration) { results = response.items.distinctBy { it.stableKey }; page = response.page; hasMore = response.hasMore }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (ticket == searchGeneration) error = friendlySourceError(failure) }
            finally { if (ticket == searchGeneration) searching = false }
        }
        return searchJob
    }
    fun more(): Job? {
        if (!hasMore || searching || error != null) return null
        val ticket = searchGeneration
        searchJob = scope.launch {
            searching = true
            try {
                val response = source.search(submitted, page + 1); ensureActive()
                if (ticket == searchGeneration) { results = (results + response.items).distinctBy { it.stableKey }; page = response.page; hasMore = response.hasMore }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (ticket == searchGeneration) { hasMore = false; notify(friendlySourceError(failure)) } }
            finally { if (ticket == searchGeneration) searching = false }
        }
        return searchJob
    }
    fun request(item: ContentItem, action: ContentAction): Job {
        cancelSelection()
        val ticket = detailsGeneration
        return scope.launch {
            readingDetails = true
            try {
                val details = source.details(BilibiliReference(item.id)); ensureActive()
                if (ticket != detailsGeneration) return@launch
                partCounts[item.id] = details.parts.size
                if (details.parts.size == 1) perform(details, details.parts.single(), action)
                else selection = PartSelection(details, action)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (ticket == detailsGeneration) notify(friendlySourceError(failure)) }
            finally { if (ticket == detailsGeneration) readingDetails = false }
        }.also { detailsJob = it }
    }
    fun choose(part: VideoPart) {
        val pending = selection ?: return
        if (pending.details.parts.none { it.cid == part.cid }) return
        selection = null
        perform(pending.details, part, pending.action)
    }
    fun cancelSelection() { ++detailsGeneration; detailsJob?.cancel(); detailsJob = null; selection = null; readingDetails = false }
    private fun perform(details: VideoDetails, part: VideoPart, action: ContentAction) {
        onAction(BilibiliTrack(details.item, part, details.parts.size), action)
        if (action != ContentAction.Play) notify(if (action == ContentAction.Next) "已设为下一首播放" else "已加入播放队列")
    }
    fun notify(text: String) { feedback = ++feedbackId to text }
}

internal fun friendlySourceError(error: Exception): String = when ((error as? SourceFailure)?.kind) {
    SourceFailureKind.LoginRequired -> "这项内容需要登录，当前尚未接入音源账号"
    SourceFailureKind.Unavailable -> "当前内容不可播放，换一个视频试试"
    SourceFailureKind.Restricted, SourceFailureKind.RateLimited -> "音源暂时限制访问，请稍后再试"
    SourceFailureKind.UnsupportedAudio -> "这项内容暂未提供可播放的音频"
    SourceFailureKind.Network -> "暂时无法连接音源，请检查网络后重试"
    else -> if (error is IllegalArgumentException) "请输入关键词、BV/AV 号或完整的视频链接" else "暂时无法读取内容，请稍后重试"
}
