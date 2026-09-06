package com.alpha.ai.imagegeneration.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

private val sharedClient by lazy {
    HttpClient(Darwin) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 600_000
            requestTimeoutMillis = 600_000
        }
    }
}

internal actual fun defaultHttpTransport(): HttpTransport = KtorHttpTransport(sharedClient)
