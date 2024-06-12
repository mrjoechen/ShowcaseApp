package com.alpha.showcase.api.unsplash

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

private const val UNSPLASH_ENDPOINT = "https://api.unsplash.com/"

private val UNSPLASH_API_TOKEN = ""

class UnsplashApi() {
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
                    Napier.i(message)
                }
            }
        }

        defaultRequest {
            header(
                HttpHeaders.Authorization,
                "Client-ID $UNSPLASH_API_TOKEN"
            )
        }

    }

    suspend fun getUserPhotos(
        username: String,
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest",
        stats: Boolean = false,
        resolution: String = "days",
        quantity: Int = 30,
        orientation: String = "landscape"
    ): List<Photo> {
        return get("users/$username/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
                parameters.append("stats", stats.toString())
                parameters.append("resolution", resolution)
                parameters.append("quantity", quantity.toString())
                parameters.append("orientation", orientation)
            }
        }
    }

    suspend fun getUserCollections(
        username: String,
        page: Int = 1,
        perPage: Int = 30
    ): List<UserCollection>{
        return get("users/$username/collections") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
            }
        }
    }

    suspend fun getUserLikes(
        username: String,
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest",
        orientation: String = "landscape"
    ): List<Photo>{
        return get("users/$username/likes") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", perPage.toString())
                parameters.append("per_page", orderBy)
                parameters.append("orientation", orientation)

            }
        }
    }

    suspend fun getCollectionPhotos(
        id: String,
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest",
        orientation: String = "landscape"
    ): List<Photo>{
        return get("collections/$id/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", perPage.toString())
                parameters.append("per_page", orderBy)
                parameters.append("orientation", orientation)

            }
        }
    }

    suspend fun getTopicPhotos(idOrSlug: String): List<Photo>{
        return get("topics/$idOrSlug/photos")
    }

    suspend fun getFeedPhotos(
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest"
    ): List<Photo>{
        return get("photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", perPage.toString())
                parameters.append("per_page", orderBy)

            }
        }
    }

    suspend fun getRandomPhotos(
        collections: String,
        featured: Boolean = false,
        username: String,
        query: String,
        orientation: String,
        contentFilter: String,
        count: Int = 20
    ): List<Photo>{
        return get("photos/random") {
            url {
                parameters.append("collections", collections)
                parameters.append("topics", featured.toString())
                parameters.append("username", username)
                parameters.append("query", query)
                parameters.append("orientation", orientation)
                parameters.append("content_filter", contentFilter)
                parameters.append("count", count.toString())
            }
        }
    }




    private suspend inline fun <reified T> get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return client.get(UNSPLASH_ENDPOINT + path, block).body()
    }
}

