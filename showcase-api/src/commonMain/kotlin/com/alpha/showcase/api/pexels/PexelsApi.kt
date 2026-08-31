package com.alpha.showcase.api.pexels

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.Log
import com.alpha.showcase.api.builtInPexelsApiKey
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

private const val PEXELS_ENDPOINT = "https://api.pexels.com/v1/"

internal fun HeadersBuilder.applyPexelsApiKey(apiKey: String) {
	append(HttpHeaders.Authorization, apiKey)
}

class PexelsApi(private val apiKey: String = builtInPexelsApiKey()) : BaseHttpClient() {
	
	override fun createLogger(): Logger = object : Logger {
		override fun log(message: String) {
			Log.d(message)
		}
	}
	
	override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
		config.defaultRequest {
			headers.applyPexelsApiKey(apiKey)
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
		return pagination.nextPage?.let { get(it) } ?: pagination
	}

	suspend fun featuredCollections(page: Int = 1, perPage: Int = 80): CollectionsPage {
		return get(PEXELS_ENDPOINT + "collections/featured") {
			url {
				parameters.append("page", page.toString())
				parameters.append("per_page", perPage.toString())
			}
		}
	}

	suspend fun myCollections(page: Int = 1, perPage: Int = 80): CollectionsPage {
		return get(PEXELS_ENDPOINT + "collections") {
			url {
				parameters.append("page", page.toString())
				parameters.append("per_page", perPage.toString())
			}
		}
	}

	suspend fun collectionPhotos(
		id: String,
		page: Int = 1,
		perPage: Int = 80,
		sort: String = "asc"
	): CollectionMediaPage {
		return get(PEXELS_ENDPOINT + "collections/$id") {
			url {
				parameters.append("type", "photos")
				parameters.append("sort", sort)
				parameters.append("page", page.toString())
				parameters.append("per_page", perPage.toString())
			}
		}
	}
}
