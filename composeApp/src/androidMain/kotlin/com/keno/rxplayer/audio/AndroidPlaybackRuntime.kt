package com.keno.rxplayer.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
actual fun rememberPlaybackRuntime(): PlaybackRuntime? {
    val context = LocalContext.current.applicationContext
    val empty = remember { MutableStateFlow<PlaybackRuntime?>(null) }
    var active by remember { mutableStateOf<StateFlow<PlaybackRuntime?>>(empty) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { active = (binder as RxPlaybackService.UiBinder).runtime }
            override fun onServiceDisconnected(name: ComponentName?) { active = empty }
        }
        context.startService(Intent(context, RxPlaybackService::class.java).setAction(RxPlaybackService.START_UI))
        val bound = context.bindService(Intent(context, RxPlaybackService::class.java).setAction(RxPlaybackService.BIND_UI), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Cannot bind playback service" }
        onDispose { context.unbindService(connection) }
    }
    val runtime by active.collectAsState()
    return runtime
}
