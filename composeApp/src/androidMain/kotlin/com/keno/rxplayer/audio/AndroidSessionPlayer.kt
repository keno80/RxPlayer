@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.keno.rxplayer.audio

import android.net.Uri
import android.os.Looper
import androidx.media3.common.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** System controls project the shared queue; stream URLs never cross the session boundary. */
class AndroidSessionPlayer(private val runtime: () -> PlaybackRuntime?) : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State {
        val p = runtime()?.playback ?: return State.Builder().setAvailableCommands(Player.Commands.EMPTY).build()
        val queue = p.queueInPlaybackOrder
        val selected = queue.indexOfFirst { it.id == p.track?.id }
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_GET_VOLUME, Player.COMMAND_SET_VOLUME,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        )
        if (p.canSeek) commands.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (queue.isNotEmpty()) commands.add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        if (queue.size > 1) commands.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        val playlist = queue.map { track ->
            val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(track.artworkUrl?.let(Uri::parse)).build()
            val duration = if (track.id == p.track?.id && p.durationMs > 0) p.durationMs else track.durationMs
            MediaItemData.Builder(track.id).setMediaItem(MediaItem.Builder().setMediaId(track.id).setMediaMetadata(metadata).build())
                .setMediaMetadata(metadata).setDurationUs(if (duration > 0) duration.toLong() * 1000 else C.TIME_UNSET)
                .setIsSeekable(duration > 0).build()
        }
        val state = when {
            selected < 0 || p.phase in setOf(PlaybackPhase.Idle, PlaybackPhase.Failed, PlaybackPhase.Unavailable, PlaybackPhase.Expired) -> Player.STATE_IDLE
            p.isWaiting -> Player.STATE_BUFFERING
            p.phase == PlaybackPhase.Ended -> Player.STATE_ENDED
            else -> Player.STATE_READY
        }
        return State.Builder().setAvailableCommands(commands.build()).setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (selected < 0) C.INDEX_UNSET else selected)
            .setPlaybackState(state).setPlayWhenReady(p.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(if (p.playWhenReady && !p.isPlaying && p.phase == PlaybackPhase.Paused) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS else Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setContentPositionMs(p.positionMs.toLong())
            .setContentBufferedPositionMs(PositionSupplier.getConstant(p.positionMs.toLong()))
            .setShuffleModeEnabled(p.shuffle).setRepeatMode(if (p.repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build())
            .setVolume(p.volume).build()
    }

    fun refresh() = invalidateState()
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        runtime()?.let { if (playWhenReady) it.play() else it.playback.pause() }
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*> {
        runtime()?.playback?.stop()
        return Futures.immediateVoidFuture()
    }
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val owner = runtime() ?: return Futures.immediateVoidFuture()
        // Media3 already computes current-item rewind, adjacent item, or INDEX_UNSET/no-op.
        val position = if (positionMs == C.TIME_UNSET) 0 else positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        owner.seekToQueueItem(mediaItemIndex, position)
        return Futures.immediateVoidFuture()
    }
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        if (repeatMode !in setOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)) return Futures.immediateFailedFuture<Void>(UnsupportedOperationException("single_track_repeat_not_supported"))
        runtime()?.playback?.let { if (it.repeat != (repeatMode == Player.REPEAT_MODE_ALL)) it.toggleRepeat() }
        return Futures.immediateVoidFuture()
    }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        runtime()?.playback?.let { if (it.shuffle != shuffleModeEnabled) it.toggleShuffle() }
        return Futures.immediateVoidFuture()
    }
    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        runtime()?.playback?.changeVolume(volume)
        return Futures.immediateVoidFuture()
    }
}
