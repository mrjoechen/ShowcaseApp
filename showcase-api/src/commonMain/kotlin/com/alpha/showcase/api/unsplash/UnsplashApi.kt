package com.alpha.showcase.api.unsplash

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.builtInUnsplashApiKey
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

private const val UNSPLASH_ENDPOINT = "https://api.unsplash.com/"

internal fun HeadersBuilder.applyUnsplashApiToken(apiToken: String) {
    append(HttpHeaders.Authorization, "Client-ID $apiToken")
}

enum class UnsplashOrientation(
    val storedValue: String,
    val queryValue: String?
) {
    All("all", null),
    Landscape("landscape", "landscape"),
    Portrait("portrait", "portrait"),
    Squarish("squarish", "squarish");

    companion object {
        fun fromStoredValue(value: String?): UnsplashOrientation {
            return entries.firstOrNull { it.storedValue == value } ?: All
        }
    }
}

class UnsplashApi(private val apiToken: String = builtInUnsplashApiKey()) : BaseHttpClient() {
    
    override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        config.defaultRequest {
            headers.applyUnsplashApiToken(apiToken)
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
        orientation: UnsplashOrientation = UnsplashOrientation.All
    ): List<Photo> {
        return get(UNSPLASH_ENDPOINT + "users/$username/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
                parameters.append("stats", stats.toString())
                parameters.append("resolution", resolution)
                parameters.append("quantity", quantity.toString())
                orientation.queryValue?.let {
                    parameters.append("orientation", it)
                }
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

    suspend fun getTopics(
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "position"
    ): List<Topic> {
        return get(UNSPLASH_ENDPOINT + "topics") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
            }
        }
    }

    suspend fun getUserLikes(
        username: String,
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest",
//        orientation: String = "landscape"
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "users/$username/likes") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
//                parameters.append("orientation", orientation)

            }
        }
    }

    suspend fun getCollectionPhotos(
        id: String,
        page: Int = 1,
        perPage: Int = 30,
        orderBy: String = "latest",
        orientation: UnsplashOrientation = UnsplashOrientation.All
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "collections/$id/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                parameters.append("order_by", orderBy)
                orientation.queryValue?.let {
                    parameters.append("orientation", it)
                }

            }
        }
    }

    suspend fun getTopicPhotos(
        idOrSlug: String,
        page: Int = 1,
        perPage: Int = 30,
        orientation: UnsplashOrientation = UnsplashOrientation.All
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "topics/$idOrSlug/photos") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
                orientation.queryValue?.let {
                    parameters.append("orientation", it)
                }
            }
        }
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
        orientation: UnsplashOrientation = UnsplashOrientation.All,
        contentFilter: String = "high",
        count: Int = 20
    ): List<Photo>{
        return get(UNSPLASH_ENDPOINT + "photos/random") {
            url {
                parameters.append("collections", collections)
                parameters.append("topics", topics)
                parameters.append("username", username)
                parameters.append("query", query)
                orientation.queryValue?.let {
                    parameters.append("orientation", it)
                }
                parameters.append("content_filter", contentFilter)
                parameters.append("count", count.toString())
            }
        }
    }




}
