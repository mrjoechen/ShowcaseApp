package com.alpha.showcase.api

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

abstract class BaseHttpClient {
    
    val client: HttpClient by lazy {
        HttpClient {
            expectSuccess = true
            install(ContentNegotiation) {
                json(createJsonConfig())
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = createLogger()
            }
            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            
            configureClient(this)
        }
    }
    
    protected open fun createJsonConfig(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    
    protected open fun createLogger(): Logger = object : Logger {
        override fun log(message: String) {
            Napier.d(message)
        }
    }
    
    protected open fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        // Override in subclasses for additional configuration
    }
    
    suspend inline fun <reified T> get(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return client.get(url, block).body()
    }
    
    suspend inline fun <reified T> post(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return client.post(url, block).body()
    }
}