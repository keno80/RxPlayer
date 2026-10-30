package com.keno.rxplayer.source

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun sourceHttpEngine(): HttpClientEngine = OkHttp.create()
