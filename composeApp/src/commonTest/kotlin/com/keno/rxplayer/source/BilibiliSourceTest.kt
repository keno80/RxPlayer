package com.keno.rxplayer.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BilibiliSourceTest {
    private fun source(body: String, status: HttpStatusCode = HttpStatusCode.OK) = BilibiliSource(HttpClient(MockEngine { request ->
        if (request.url.host == "www.bilibili.com") respond("ok")
        else respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }))

    @Test fun stableIdentityKeepsPartAndRejectsForeignLinks() {
        assertEquals(BilibiliReference("BV14u41147YH", 2), BilibiliReference.parse("https://www.bilibili.com/video/BV14u41147YH/?p=2&tracking=test"))
        assertEquals(BilibiliReference("av528990022"), BilibiliReference.parse("AV528990022"))
        for (input in listOf("av0", "BVshort", "https://bilibili.com.evil.test/video/BV14u41147YH", "https://u@www.bilibili.com/video/BV14u41147YH", "https://www.bilibili.com/video/BV14u41147YH?p=0", "https://www.bilibili.com/video/BV14u41147YH?p=")) {
            assertFailsWith<IllegalArgumentException> { BilibiliReference.parse(input) }
        }
    }

    @Test fun searchPassesQueryAndPageAndStripsHighlightMarkup() = runTest {
        val requests = mutableListOf<Url>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url
            respond(if (request.url.host == "www.bilibili.com") "ok" else """{"code":0,"data":{"page":2,"numPages":3,"result":[{"bvid":"BV14u41147YH","title":"<em class=\"keyword\">Blender</em> &amp; music","author":"Studio","pic":"//i0.hdslb.com/cover.jpg","duration":"01:02"},{"type":"ad"}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val source = BilibiliSource(client)
        try {
            val page = source.search("Blender 音乐", 2)
            assertEquals("Blender 音乐", requests.last().parameters["keyword"])
            assertEquals("2", requests.last().parameters["page"])
            assertEquals("video", requests.last().parameters["search_type"])
            assertEquals("Blender & music", page.items.single().title)
            assertEquals(62, page.items.single().durationSeconds)
            assertEquals("https://i0.hdslb.com/cover.jpg", page.items.single().artworkUrl)
            assertTrue(page.hasMore)
        } finally { source.close() }
    }

    @Test fun metadataKeepsEveryPartAndCanonicalBv() = runTest {
        val source = source("""{"code":0,"data":{"bvid":"BV14u41147YH","aid":528990022,"title":"Course","pic":"https://i0.hdslb.com/a.jpg","duration":90,"owner":{"name":"Creator"},"pages":[{"page":1,"cid":101,"part":"First","duration":40},{"page":2,"cid":102,"part":"Second","duration":50}]}}""")
        try {
            val detail = source.details(BilibiliReference("av528990022", 2))
            assertEquals("BV14u41147YH", detail.item.id)
            assertEquals(listOf(101L, 102L), detail.parts.map { it.cid })
            assertEquals(2, detail.selectedPart.number)
        } finally { source.close() }
    }

    @Test fun resolveSelectsSupportedAacAndDoesNotExposeSignedUrlInToString() = runTest {
        val source = source("""{"code":0,"data":{"dash":{"audio":[{"mimeType":"audio/mp4","codecs":"ec-3","bandwidth":900000,"baseUrl":"https://a.bilivideo.com/unsupported"},{"mimeType":"audio/mp4","codecs":"mp4a.40.2","bandwidth":128000,"baseUrl":"https://a.bilivideo.com/audio?deadline=1800000000&sign=secret"}]}}}""")
        try {
            val stream = source.resolve("BV14u41147YH", 102)
            assertEquals("mp4a.40.2", stream.codec)
            assertEquals(1800000000L, stream.expiresAtEpochSeconds)
            assertEquals("https://www.bilibili.com/", stream.headers[HttpHeaders.Referrer])
            assertFalse(stream.toString().contains("secret"))
            assertFalse(stream.toString().contains("https://"))
        } finally { source.close() }
    }

    @Test fun unavailableIsDifferentFromLoginAndRiskControl() = runTest {
        for ((code, expected) in listOf(62002 to SourceFailureKind.Unavailable, -101 to SourceFailureKind.LoginRequired, -412 to SourceFailureKind.Restricted)) {
            val source = source("""{"code":$code}""")
            try { assertEquals(expected, assertFailsWith<SourceFailure> { source.search("sample") }.kind) }
            finally { source.close() }
        }
    }

    @Test fun malformedJsonAndAbsentAudioAreNotClaimedAsLoginRequired() = runTest {
        val invalid = source("not json")
        val absent = source("""{"code":0,"data":{"dash":{"audio":[]}}}""")
        try {
            assertEquals(SourceFailureKind.InvalidResponse, assertFailsWith<SourceFailure> { invalid.search("sample") }.kind)
            assertEquals(SourceFailureKind.UnsupportedAudio, assertFailsWith<SourceFailure> { absent.resolve("BV14u41147YH", 101) }.kind)
        } finally { invalid.close(); absent.close() }
    }

    @Test fun rateLimitIsExplicitAndNoRawResponseIsExposed() = runTest {
        val source = source("secret signed-url", HttpStatusCode.TooManyRequests)
        try {
            val failure = assertFailsWith<SourceFailure> { source.search("sample") }
            assertEquals(SourceFailureKind.RateLimited, failure.kind)
            assertFalse(failure.toString().contains("secret"))
        } finally { source.close() }
    }

    @Test fun oversizedOrMalformedMetadataIsRejected() = runTest {
        val oversized = source(" ".repeat(4 * 1024 * 1024 + 1))
        val malformed = source("""{"code":0,"data":{"bvid":"BV14u41147YH","pages":["not a part"]}}""")
        try {
            assertEquals(SourceFailureKind.InvalidResponse, assertFailsWith<SourceFailure> { oversized.search("sample") }.kind)
            assertEquals(SourceFailureKind.InvalidResponse, assertFailsWith<SourceFailure> { malformed.details(BilibiliReference("BV14u41147YH")) }.kind)
        } finally { oversized.close(); malformed.close() }
    }

    @Test fun mediaCannotPointToLocalOrCleartextTargets() = runTest {
        for (url in listOf("https://127.0.0.1/private", "http://a.bilivideo.com/audio", "https://bilivideo.com.evil.test/audio")) {
            val source = source("""{"code":0,"data":{"dash":{"audio":[{"mimeType":"audio/mp4","codecs":"mp4a.40.2","baseUrl":"$url"}]}}}""")
            try { assertEquals(SourceFailureKind.InvalidResponse, assertFailsWith<SourceFailure> { source.resolve("BV14u41147YH", 101) }.kind) }
            finally { source.close() }
        }
    }

    @Test fun validCdnMayUseExplicitHttpsPort() = runTest {
        val url = "https://sample.mcdn.bilivideo.cn:8082/audio?deadline=1800000000"
        val source = source("""{"code":0,"data":{"dash":{"audio":[{"mimeType":"audio/mp4","codecs":"mp4a.40.2","baseUrl":"$url"}]}}}""")
        try { assertEquals(url, source.resolve("BV14u41147YH", 101).url) }
        finally { source.close() }
    }
}
