package com.alpha.showcase.api.appstore

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.Log
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

private const val ITUNES_LOOKUP_ENDPOINT = "https://itunes.apple.com/lookup"

class AppStoreApi(private val clientOverride: HttpClient? = null) : BaseHttpClient() {

    override fun createLogger(): Logger = object : Logger {
        override fun log(message: String) {
            Log.d(message)
        }
    }

    suspend fun lookup(appId: String): AppStoreLookupResponse {
        // Apple returns JSON as text/javascript on some storefronts/CDN responses.
        // Decode this endpoint explicitly rather than relaxing every API's content negotiation.
        val body = (clientOverride ?: client).get(ITUNES_LOOKUP_ENDPOINT) {
            url { parameters.append("id", appId) }
        }.bodyAsText()
        return createJsonConfig().decodeFromString<AppStoreLookupResponse>(body)
    }
}
