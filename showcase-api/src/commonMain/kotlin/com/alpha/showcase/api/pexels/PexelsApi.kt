package com.alpha.showcase.api.pexels

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.Log
import com.alpha.showcase.api.PEXELS_API_KEY
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private const val PEXELS_ENDPOINT = "https://api.pexels.com/v1/"

class PexelsApi(private val apiKey: String = PEXELS_API_KEY) : BaseHttpClient() {
	
	override fun createLogger(): Logger = object : Logger {
		override fun log(message: String) {
			Log.d(message)
		}
	}
	
	override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
		config.defaultRequest {
			header(HttpHeaders.Authorization, apiKey)
		}
	}

	suspend fun curatedPhotos(page: Int = 1, perPage: Int = 15): Pagination {
		return get(PEXELS_ENDPOINT + "curated") {
			url {
				if (page != 1) {
					parameters.append("page", page.toString())
				}
				parameters.append("per_page", perPage.toString())
			}
		}
	}

	suspend fun curatedNextPagePhotos(pagination: Pagination): Pagination {
		return get(pagination.nextPage)
	}
}