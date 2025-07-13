package com.alpha.showcase.api.github

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.Log
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private const val GITHUB_ENDPOINT = "https://api.github.com/"

class GithubApi(private val auth: String? = null) : BaseHttpClient() {
    
    override fun createLogger(): Logger = object : Logger {
        override fun log(message: String) {
            Log.d(message)
        }
    }
    
    override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        if (!auth.isNullOrBlank()) {
            config.defaultRequest {
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
    ): List<GithubFile> {
        return get(GITHUB_ENDPOINT + "repos/$owner/$repo/contents/$path") {
            url {
                if (!branch.isNullOrBlank()){
                    parameters.append("ref", branch)
                }
            }
        }
    }
}