package com.keno.rxplayer.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

class PlaybackState(private val player: AudioPlayer) {
    private val selectionMutex = Mutex()
    var track: DemoTrack? by mutableStateOf(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var error: String? by mutableStateOf(null)
        private set

    @OptIn(ExperimentalResourceApi::class)
    suspend fun select(next: DemoTrack) = selectionMutex.withLock {
        player.pause()
        track = next
        isPlaying = false
        positionMs = 0
        durationMs = 0
        error = null
        isLoading = true
        try {
            player.load(Res.readBytes(next.resourcePath))
            durationMs = player.durationMs
            player.play()
            sync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "音频无法播放：${failure.message ?: "请检查音频输出设备"}"
        } finally {
            isLoading = false
        }
    }

    fun toggle() {
        if (track == null || isLoading || error != null) return
        if (player.isPlaying) player.pause() else {
            if (player.positionMs >= player.durationMs) player.seekTo(0)
            player.play()
        }
        sync()
    }

    fun seek(position: Int) {
        if (isLoading || durationMs == 0 || error != null) return
        player.seekTo(position.coerceIn(0, durationMs))
        sync()
    }

    fun pause() {
        if (isLoading) return
        player.pause()
        sync()
    }

    fun sync() {
        if (isLoading && durationMs == 0) return
        isPlaying = player.isPlaying
        positionMs = player.positionMs.coerceIn(0, durationMs)
    }
}
