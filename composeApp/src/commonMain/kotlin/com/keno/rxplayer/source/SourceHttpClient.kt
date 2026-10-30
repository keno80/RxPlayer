package com.keno.rxplayer.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.headers
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

internal const val BILIBILI_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
internal expect fun sourceHttpEngine(): HttpClientEngine

fun createSourceHttpClient(): HttpClient = HttpClient(sourceHttpEngine()) {
    expectSuccess = false
    install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
    install(HttpCookies)
    defaultRequest {
        headers {
            append(HttpHeaders.UserAgent, BILIBILI_USER_AGENT)
            append(HttpHeaders.Referrer, "https://www.bilibili.com/")
        }
    }
}
