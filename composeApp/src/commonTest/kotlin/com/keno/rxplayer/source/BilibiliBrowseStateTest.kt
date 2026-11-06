@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.keno.rxplayer.source

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.test.*

class BilibiliBrowseStateTest {
    private val firstId = "BV14u41147YH"
    private val secondId = "BV14u41147YA"
    private val thirdId = "BV14u41147YB"
    private fun page(vararg ids: String, pages: Int = 2) = """{"code":0,"data":{"numPages":$pages,"result":[${ids.joinToString { """{"bvid":"$it","title":"Video","author":"Creator","duration":"0:30"}""" }}]}}"""

    @Test fun failedPageCanBeExplicitlyRetriedWithoutLosingResultsOrUsingDraftQuery() = runTest {
        var fail = true
        val requests = mutableListOf<Pair<String?, String?>>()
        val source = BilibiliSource(HttpClient(MockEngine { request ->
            if (request.url.host == "www.bilibili.com") respond("ok")
            else {
                val requestedPage = request.url.parameters["page"]
                requests += request.url.parameters["keyword"] to requestedPage
                if (requestedPage == "2" && fail) respond("private response", HttpStatusCode.ServiceUnavailable)
                else respond(if (requestedPage == "2") page(secondId, thirdId) else page(firstId, secondId), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        try {
            val browse = BilibiliBrowseState(source, this) { _, _ -> fail("Browsing must not change playback") }
            browse.query = "已提交的搜索"; browse.submit()?.join()
            browse.query = "尚未提交的草稿"
            browse.more()?.join()
            assertEquals(listOf(firstId, secondId), browse.results.map { it.id })
            assertNull(browse.error); assertNotNull(browse.pageError); assertTrue(browse.hasMore)
            assertNull(browse.more(), "Scrolling must not automatically retry a failed page")
            fail = false
            browse.retryMore()?.join()
            assertEquals(listOf(firstId, secondId, thirdId), browse.results.map { it.id })
            assertEquals(listOf("1", "2", "2"), requests.map { it.second })
            assertTrue(requests.all { it.first == "已提交的搜索" })
            assertEquals("尚未提交的草稿", browse.query)
            assertNull(browse.pageError); assertFalse(browse.hasMore)
            assertFalse(browse.feedback!!.second.contains("private"))
        } finally { source.close() }
    }

    @Test fun duplicatePaginationAndLateCancelledResponseCannotOverwriteANewSearch() = runTest {
        val gate = CompletableDeferred<Unit>()
        var pageTwoCalls = 0
        val source = BilibiliSource(HttpClient(MockEngine { request ->
            if (request.url.host == "www.bilibili.com") respond("ok")
            else {
                val body = when {
                    request.url.parameters["page"] == "2" -> { pageTwoCalls++; withContext(NonCancellable) { gate.await() }; page(secondId) }
                    request.url.parameters["keyword"] == "new" -> page(thirdId, pages = 1)
                    else -> page(firstId)
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        try {
            val browse = BilibiliBrowseState(source, this) { _, _ -> fail("Unexpected action") }
            browse.query = "old"; browse.submit()?.join()
            val pending = browse.more()
            assertNull(browse.more(), "Only one request may own a page")
            runCurrent()
            browse.query = "new"; browse.submit()?.join()
            gate.complete(Unit); pending?.join()
            assertEquals(1, pageTwoCalls)
            assertEquals(listOf(thirdId), browse.results.map { it.id })
            assertEquals("new", browse.submitted)
            assertNull(browse.error); assertNull(browse.pageError); assertFalse(browse.searching)
        } finally { gate.complete(Unit); source.close() }
    }

    @Test fun initialSearchRetryCanFinishAsAnActualEmptyResult() = runTest {
        var fail = true
        val source = BilibiliSource(HttpClient(MockEngine { request ->
            if (request.url.host == "www.bilibili.com") respond("ok")
            else if (fail) respond("private", HttpStatusCode.ServiceUnavailable)
            else respond(page(pages = 0), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        try {
            val browse = BilibiliBrowseState(source, this) { _, _ -> fail("Unexpected action") }
            browse.query = "not found"; browse.submit()?.join()
            assertNotNull(browse.error); assertFalse(browse.searching)
            fail = false; browse.submit()?.join()
            assertTrue(browse.results.isEmpty()); assertNull(browse.error)
            assertFalse(browse.hasMore); assertFalse(browse.searching)
        } finally { source.close() }
    }

    @Test fun failedDetailsKeepSearchResultsAndNeverInvokeAPlaybackOrQueueAction() = runTest {
        for ((status, kind) in listOf(HttpStatusCode.Unauthorized to SourceFailureKind.LoginRequired,
            HttpStatusCode.Gone to SourceFailureKind.Unavailable, HttpStatusCode.Forbidden to SourceFailureKind.Restricted,
            HttpStatusCode.ServiceUnavailable to SourceFailureKind.Network)) {
            val source = BilibiliSource(HttpClient(MockEngine { request ->
                when {
                    request.url.host == "www.bilibili.com" -> respond("ok")
                    request.url.encodedPath.endsWith("/search/type") -> respond(page(firstId, pages = 1), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    else -> respond("private signed response", status)
                }
            }))
            try {
                val browse = BilibiliBrowseState(source, this) { _, _ -> fail("A failed request must not change playback or queue") }
                browse.query = "sample"; browse.submit()?.join()
                val before = browse.results
                browse.request(before.single(), ContentAction.Play).join()
                assertEquals(before, browse.results)
                assertNull(browse.selection); assertFalse(browse.readingDetails)
                assertEquals(friendlySourceError(SourceFailure(kind)), browse.feedback?.second)
                browse.cancelSelection()
                assertEquals(before, browse.results)
            } finally { source.close() }
        }
    }

    @Test fun cancelKeepsPlaybackAndQueueUntouchedAndEachConfirmedCidIsIndependent() = runTest {
        val source = BilibiliSource(HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath == "/x/web-interface/view") """{"code":0,"data":{"bvid":"BV14u41147YH","title":"Video","owner":{"name":"Artist"},"duration":30,"pages":[{"page":1,"cid":11,"part":"One","duration":10},{"page":2,"cid":22,"part":"Two","duration":20}]}}""" else "{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        val actions = mutableListOf<Pair<BilibiliTrack, ContentAction>>()
        val browse = BilibiliBrowseState(source, this) { track, action -> actions += track to action }
        val item = ContentItem(MusicSource.Bilibili, "BV14u41147YH", "Video", "Artist", null, 30)
        browse.request(item, ContentAction.Play).join()
        assertNotNull(browse.selection); assertTrue(actions.isEmpty())
        browse.cancelSelection(); assertNull(browse.selection); assertTrue(actions.isEmpty())
        browse.request(item, ContentAction.Enqueue).join(); browse.choose(browse.selection!!.details.parts[1])
        assertEquals(22, actions.single().first.part.cid)
        assertEquals(ContentAction.Enqueue, actions.single().second)
        browse.request(item, ContentAction.Next).join(); browse.choose(browse.selection!!.details.parts[0])
        assertEquals(ContentAction.Next, actions.last().second)
        assertNotEquals(actions[0].first.id, actions[1].first.id)
        source.close()
    }
}
