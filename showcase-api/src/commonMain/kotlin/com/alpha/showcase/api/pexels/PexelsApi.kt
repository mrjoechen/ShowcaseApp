package com.alpha.showcase.api.pexels

import com.alpha.showcase.api.pexels.model.Pagination
import io.github.aakira.napier.Napier
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

private const val ENDPOINT = "https://api.pexels.com/v1/"

class PexelsApi(apiKey: String) {
	private val client = HttpClient {
		expectSuccess = true
		install(ContentNegotiation) {
			json(Json {
				ignoreUnknownKeys = true
			})
		}
		install(Logging) {
			level = LogLevel.ALL
			logger = object : Logger {
				override fun log(message: String) {
					Napier.i(message)
				}
			}
		}
		defaultRequest {
			header(HttpHeaders.Authorization, apiKey)
		}
	}

	/**
	 * Get real-time photos curated by the Pexels team.
	 *
	 * @param perPage The number of results you are requesting per page. default: 15 max: 80
	 */
	suspend fun curatedPhotos(page: Int = 1, perPage: Int = 15): Pagination {
		return get("curated") {
			url {
				if (page != 1) {
					parameters.append("page", page.toString())
				}
				parameters.append("per_page", perPage.toString())
			}
		}
	}

	private suspend inline fun <reified T> get(
		path: String,
		block: HttpRequestBuilder.() -> Unit = {}
	): T {
		return client.get(ENDPOINT + path, block).body()
	}
}