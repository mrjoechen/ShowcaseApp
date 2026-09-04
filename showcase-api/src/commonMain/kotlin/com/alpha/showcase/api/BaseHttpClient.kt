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

abstract class BaseHttpClient(
    private val addDefaultJsonContentType: Boolean = true,
) {
    
    val client: HttpClient by lazy {
        HttpClient {
            expectSuccess = true
            install(ContentNegotiation) {
                json(createJsonConfig())
            }
            install(Logging) {
                // Request/response bodies can contain credentials and access tokens.
                // INFO keeps method/status visibility without retaining either payload.
                level = LogLevel.INFO
                sanitizeHeader { header ->
                    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                        header.equals("x-api-key", ignoreCase = true)
                }
                logger = createLogger()
            }
            if (addDefaultJsonContentType) {
                install(DefaultRequest) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
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
            Napier.d(redactSensitiveQueryParameters(message))
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

private val sensitiveQueryParameter =
    Regex(
        "([?&](?:auth_code|api_key|access_token|refresh_token)=)[^&\\s]*",
        RegexOption.IGNORE_CASE,
    )

private fun redactSensitiveQueryParameters(message: String): String =
    sensitiveQueryParameter.replace(message) { match ->
        "${match.groupValues[1]}<redacted>"
    }
