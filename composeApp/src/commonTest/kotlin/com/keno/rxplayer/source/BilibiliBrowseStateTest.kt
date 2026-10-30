package com.keno.rxplayer.source

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class BilibiliBrowseStateTest {
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
