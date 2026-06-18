package com.alpha.showcase.api.appstore

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.Log
import io.ktor.client.plugins.logging.Logger

private const val ITUNES_LOOKUP_ENDPOINT = "https://itunes.apple.com/lookup"

class AppStoreApi : BaseHttpClient() {

    override fun createLogger(): Logger = object : Logger {
        override fun log(message: String) {
            Log.d(message)
        }
    }

    suspend fun lookup(appId: String): AppStoreLookupResponse {
        return get("$ITUNES_LOOKUP_ENDPOINT?id=$appId")
    }
}
