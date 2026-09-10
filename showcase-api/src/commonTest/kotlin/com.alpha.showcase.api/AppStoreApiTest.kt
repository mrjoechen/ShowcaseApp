package com.alpha.showcase.api

import com.alpha.showcase.api.appstore.AppStoreApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppStoreApiTest {
    @Test fun lookupAcceptsApplesJavascriptContentType() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"resultCount":1,"results":[{"version":"2.1.0","unknown":"ignored"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"))
        }) { install(ContentNegotiation) { json() } }
        try { assertEquals("2.1.0", AppStoreApi(client).lookup("123").results.single().version) }
        finally { client.close() }
    }
}
