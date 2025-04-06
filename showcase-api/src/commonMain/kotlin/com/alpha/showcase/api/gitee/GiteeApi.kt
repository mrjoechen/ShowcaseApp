package com.alpha.showcase.api.gitee

import com.alpha.showcase.api.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val GITEE_BASE_URL = "https://gitee.com/api/v5/"

class GiteeApi(auth: String? = null) {
    private val client = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(message)
                }
            }
        }

        if(!auth.isNullOrBlank()){
            defaultRequest {
                header(
                    HttpHeaders.Authorization,
                    if (auth.startsWith("token")) auth else "token $auth"
                )
            }
        }

    }

    suspend fun getFiles(
        owner: String,
        repo: String,
        path: String,
        branch: String?
    ): List<GiteeFile> {
        return get("repos/$owner/$repo/contents/$path") {
            url {
                if (!branch.isNullOrBlank()){
                    parameters.append("ref", branch)
                }
            }
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return client.get(GITEE_BASE_URL + path, block).body()
    }
}