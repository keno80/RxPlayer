@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.keno.rxplayer.audio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.content.ContextCompat
import com.keno.rxplayer.persistence.LibraryStore
import com.keno.rxplayer.source.BilibiliSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RxPlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = MutableStateFlow<PlaybackRuntime?>(null)
    private lateinit var engine: AudioPlayer
    private lateinit var source: BilibiliSource
    private lateinit var sessionPlayer: AndroidSessionPlayer
    private lateinit var session: MediaSession
    private val uiBinder = UiBinder()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Also cancel address resolution before ExoPlayer has registered its own receiver.
                active.value?.playback?.pause()
                sessionPlayer.refresh()
            }
        }
    }
    inner class UiBinder : Binder() { val runtime: StateFlow<PlaybackRuntime?> get() = active }

    override fun onCreate() {
        super.onCreate()
        engine = createAndroidAudioPlayer(this)
        source = BilibiliSource()
        sessionPlayer = AndroidSessionPlayer { active.value }
        val builder = MediaSession.Builder(this, sessionPlayer).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                if (allowed(controller)) super.onConnect(session, controller) else MediaSession.ConnectionResult.reject()
        })
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        session = builder.build()
        addSession(session)
        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                active.value?.let { it.playback.pause(); it.warn("系统限制后台播放，请回到应用重试") }
            }
        })
        scope.launch {
            active.value = openPlaybackRuntime(engine, LibraryStore(filesDir.resolve("library.db").absolutePath),
                { source.resolve(it.item.id, it.part.cid) }, { engine.release(); source.close() })
            var previous: Any? = null
            while (isActive) {
                val p = active.value!!.playback
                val state = listOf(p.checkpoint(periodic = true), p.phase, p.durationMs, p.playWhenReady, p.canSeek)
                if (state != previous) { sessionPlayer.refresh(); previous = state }
                delay(100)
            }
        }
    }

    private fun allowed(controller: MediaSession.ControllerInfo) = controller.packageName == packageName || controller.isTrusted
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = if (allowed(controllerInfo)) session else null
    override fun onBind(intent: Intent?): IBinder? {
        val systemBinder = super.onBind(intent)
        return if (intent?.action == BIND_UI) uiBinder else systemBinder
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        if (intent?.action == START_UI) START_NOT_STICKY else super.onStartCommand(intent, flags, startId)

    override fun onDestroy() {
        val runtime = active.value
        active.value = null
        scope.cancel()
        unregisterReceiver(noisyReceiver)
        session.release()
        sessionPlayer.release()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            withContext(NonCancellable) {
                if (runtime != null) runtime.close() else { engine.release(); source.close() }
            }
        }
        super.onDestroy()
    }
    companion object {
        const val BIND_UI = "com.keno.rxplayer.BIND_PLAYBACK_UI"
        const val START_UI = "com.keno.rxplayer.START_PLAYBACK_UI"
    }
}
