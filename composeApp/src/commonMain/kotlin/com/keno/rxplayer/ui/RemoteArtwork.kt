package com.keno.rxplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.source.createSourceHttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class CoverImage(val bitmap: ImageBitmap, val seed: Int)
internal expect fun decodeCover(bytes: ByteArray): ImageBitmap
internal class ArtworkStore {
    private val http = createSourceHttpClient()
    private val permits = Semaphore(4)
    private val covers = mutableStateMapOf<String, CoverImage>()
    fun get(url: String?) = url?.let(covers::get)
    suspend fun load(url: String?) {
        if (url == null || covers.containsKey(url)) return
        try {
            val image = permits.withPermit {
                http.prepareGet(url).execute { response ->
                    check(response.status.value in 200..299)
                    val bytes = response.bodyAsChannel().readRemaining(4L * 1024 * 1024 + 1).readByteArray()
                    check(bytes.size <= 4 * 1024 * 1024)
                    withContext(Dispatchers.Default) {
                        val bitmap = decodeCover(bytes)
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.readPixels(pixels)
                        val buckets = mutableMapOf<Int, Int>()
                        for (i in pixels.indices step 19) {
                            val color = pixels[i]; val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
                            if ((r + g + b) in 100..660 && (color ushr 24) > 128) {
                                val key = (r / 24 shl 8) or (g / 24 shl 4) or (b / 24)
                                buckets[key] = (buckets[key] ?: 0) + 1
                            }
                        }
                        val bucket = buckets.maxByOrNull { it.value }?.key
                        val seed = bucket?.let { 0xFF000000.toInt() or (((it shr 8) * 24 + 12) shl 16) or (((it shr 4 and 15) * 24 + 12) shl 8) or ((it and 15) * 24 + 12) } ?: 0xFF006A62.toInt()
                        CoverImage(bitmap, seed)
                    }
                }
            }
            if (covers.size >= 64) covers.remove(covers.keys.first())
            covers[url] = image
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Neutral placeholder; never substitute a fictional cover. */ }
    }
    fun close() = http.close()
}
internal val LocalArtworkStore = staticCompositionLocalOf<ArtworkStore> { error("ArtworkStore missing") }

@Composable
fun Artwork(track: PlaybackTrack, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    if (track is DemoTrack) Artwork(track.artwork, modifier, shape) else RemoteArtwork(track.artworkUrl, modifier, shape)
}

@Composable
internal fun RemoteArtwork(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val store = LocalArtworkStore.current
    LaunchedEffect(url) { store.load(url) }
    val image = store.get(url)
    if (image != null) Image(image.bitmap, null, modifier.clip(shape), contentScale = ContentScale.Crop)
    else Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
