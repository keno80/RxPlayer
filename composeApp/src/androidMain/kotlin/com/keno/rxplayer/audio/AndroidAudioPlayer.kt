@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.keno.rxplayer.audio

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.keno.rxplayer.source.AudioStream
import java.io.File
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { createAndroidAudioPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** Must be created and controlled on the main looper, including in instrumentation. */
fun createAndroidAudioPlayer(context: Context): AudioPlayer = AndroidAudioPlayer(context.applicationContext)

private class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val http = OkHttpClient()
    private var temporary: File? = null
    private var generation = 0
    private var deadline: Long? = null
    private var released = false
    override var failure: PlaybackFailure? = null
        private set
    init { player.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) { failure = classify(error) }
    }) }
    override val durationMs get() = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
    override val positionMs get() = player.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    override val isPlaying get() = player.isPlaying
    override val isBuffering get() = player.playbackState == Player.STATE_BUFFERING
    override val isEnded get() = player.playbackState == Player.STATE_ENDED

    override suspend fun load(bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) { File.createTempFile("rxplayer-", ".wav", context.cacheDir).apply { writeBytes(bytes) } }
        try { prepare(file.toURI().toString(), emptyMap(), null, file) }
        catch (error: Exception) { file.delete(); throw error }
    }
    override suspend fun load(stream: AudioStream) = prepare(stream.url, stream.headers, stream.expiresAtEpochSeconds, null)

    private suspend fun prepare(uri: String, headers: Map<String, String>, expires: Long?, file: File?) = withContext(Dispatchers.Main.immediate) {
        check(!released)
        val token = ++generation
        player.stop()
        temporary?.delete(); temporary = file
        deadline = expires; failure = null
        val factory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers))
        val media = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(uri))
        try { withTimeout(30_000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && continuation.isActive) {
                            player.removeListener(this); continuation.resume(Unit)
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resumeWithException(PlaybackExceptionSafe(classify(error)))
                    }
                }
                player.addListener(listener)
                continuation.invokeOnCancellation {
                    android.os.Handler(player.applicationLooper).post {
                        if (!released) { player.removeListener(listener); if (generation == token) player.stop() }
                    }
                }
                player.setMediaSource(media); player.prepare()
            }
        } } catch (_: TimeoutCancellationException) { throw PlaybackExceptionSafe(PlaybackFailure()) }
    }
    private fun classify(error: PlaybackException): PlaybackFailure {
        val code = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        val expired = code == 410 || (code in listOf(401, 403) && deadline?.let { System.currentTimeMillis() / 1000 >= it } == true)
        return PlaybackFailure(if (expired) PlaybackPhase.Expired else if (code in listOf(401, 403, 404)) PlaybackPhase.Unavailable else PlaybackPhase.Failed)
    }
    override fun play() { player.play() }
    override fun pause() { player.pause() }
    override fun seekTo(positionMs: Int) { player.seekTo(positionMs.toLong()) }
    override fun setVolume(volume: Float) { player.volume = volume.coerceIn(0f, 1f) }
    override fun release() {
        if (released) return
        released = true; generation++; player.release(); temporary?.delete(); temporary = null
        // Closing a live pooled socket performs network I/O under Android StrictMode.
        CoroutineScope(Dispatchers.IO).launch { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
    }
}
