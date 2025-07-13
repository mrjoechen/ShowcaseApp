package com.alpha.showcase.api.unsplash

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.UNSPLASH_API_KEY
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private const val UNSPLASH_ENDPOINT = "https://api.unsplash.com/"

private val UNSPLASH_API_TOKEN = UNSPLASH_API_KEY

class UnsplashApi(private val apiToken: String = UNSPLASH_API_TOKEN) : BaseHttpClient() {
    
    override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        config.defaultRequest {
            header(
                HttpHeaders.Authorization,
                "Client-ID $apiToken"
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
        return get(UNSPLASH_ENDPOINT + "users/$username/photos") {
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
        return get(UNSPLASH_ENDPOINT + "users/$username/collections") {
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
        return get(UNSPLASH_ENDPOINT + "users/$username/likes") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
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
        return get(UNSPLASH_ENDPOINT + "collections/$id/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
                parameters.append("orientation", orientation)

            }
        }
    }

    suspend fun getTopicPhotos(idOrSlug: String): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "topics/$idOrSlug/photos")
    }

    suspend fun getFeedPhotos(
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest"
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)

            }
        }
    }

    suspend fun getRandomPhotos(
        collections: String,
        topics: String,
        username: String,
        query: String,
        orientation: String = "landscape",
        contentFilter: String = "high",
        count: Int = 20
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "photos/random") {
            url {
                parameters.append("collections", collections)
                parameters.append("topics", topics)
                parameters.append("username", username)
                parameters.append("query", query)
                parameters.append("orientation", orientation)
                parameters.append("content_filter", contentFilter)
                parameters.append("count", count.toString())
            }
        }
    }




}

