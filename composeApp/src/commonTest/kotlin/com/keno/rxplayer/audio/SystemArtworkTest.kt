package com.keno.rxplayer.audio

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SystemArtworkTest {
    private val png = intArrayOf(137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82,0,0,0,1,0,0,0,1,8,6,0,0,0,31,21,196,137,0,0,0,13,73,68,65,84,120,156,99,80,72,104,248,15,0,3,164,2,0,133,134,188,85,0,0,0,0,73,69,78,68,174,66,96,130).map { it.toByte() }.toByteArray()
    @Test fun missingAlbumImageFallsBackToOriginalVideoWithoutUnboundedRequests() = runTest {
        val requests = mutableListOf<String>()
        val bytes = png
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.encodedPath == "/album") respond("not found", HttpStatusCode.NotFound)
            else respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
        })
        try {
            val image = assertNotNull(fetchSystemArtwork(client, "https://p1.music.126.net/album", "https://i0.hdslb.com/video"))
            assertEquals("https://i0.hdslb.com/video", image.url)
            assertContentEquals(bytes, image.bytes)
            assertEquals(2, requests.size)
        } finally { client.close() }
    }

    @Test fun oversizedOrNonImageResponseDoesNotBecomeSystemArtwork() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/album") respond("huge", headers = headersOf(HttpHeaders.ContentLength to listOf("5000000"), HttpHeaders.ContentType to listOf("image/jpeg")))
            else respond("html error", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try { assertNull(fetchSystemArtwork(client, "https://p1.music.126.net/album", "https://i0.hdslb.com/video")) }
        finally { client.close() }
    }

    @Test fun cancelledImageRequestDoesNotStartFallbackAfterSwitchingTracks() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; throw CancellationException("cancel") })
        try {
            assertFailsWith<CancellationException> { fetchSystemArtwork(client, "https://p1.music.126.net/album", "https://i0.hdslb.com/video") }
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun successfulHttpWithDamagedImageStillFallsBackToDecodableVideoArtwork() = runTest {
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath == "/album") ByteArray(100) { 42 } else png,
                headers = headersOf(HttpHeaders.ContentType, "image/png"))
        })
        try { assertEquals("https://i0.hdslb.com/video", fetchSystemArtwork(client, "https://p1.music.126.net/album", "https://i0.hdslb.com/video")?.url) }
        finally { client.close() }
    }
}
