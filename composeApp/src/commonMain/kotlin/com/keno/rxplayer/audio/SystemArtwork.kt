package com.keno.rxplayer.audio

import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.ui.decodeCover
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

internal data class SystemArtwork(val url: String, val bytes: ByteArray)
internal fun systemArtworkKey(track: PlaybackTrack?) = track?.let { listOf(it.id, it.artworkUrl.orEmpty(), it.fallbackArtworkUrl.orEmpty()).joinToString("\u0001") }.orEmpty()

/** Compressed thumbnails stay small enough for Android's cross-process metadata transport. */
internal suspend fun fetchSystemArtwork(client: HttpClient, primary: String?, fallback: String?): SystemArtwork? {
    val limit = 256 * 1024
    for (original in listOfNotNull(primary, fallback).distinct()) {
        try {
            val parsed = Url(original)
            val address = if (parsed.host == "music.126.net" || parsed.host.endsWith(".music.126.net"))
                URLBuilder(parsed).apply { parameters["param"] = "500y500" }.buildString() else original
            val image = client.prepareGet(address).execute { response ->
                if (response.status.value !in 200..299 || !response.headers[HttpHeaders.ContentType].orEmpty().startsWith("image/", ignoreCase = true) ||
                    (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > limit) null
                else response.bodyAsChannel().readRemaining(limit + 1L).readByteArray().takeIf { it.size in 16..limit }?.let { bytes ->
                    withContext(Dispatchers.Default) {
                        // Reuse the platform decoder and its image-dimension budget; force pixel decode before accepting bytes.
                        decodeCover(bytes).readPixels(IntArray(1), width = 1, height = 1)
                    }
                    SystemArtwork(address, bytes)
                }
            }
            currentCoroutineContext().ensureActive()
            if (image != null) return image
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Try the video's original artwork once; never retry either URL in a loop. */ }
    }
    return null
}
