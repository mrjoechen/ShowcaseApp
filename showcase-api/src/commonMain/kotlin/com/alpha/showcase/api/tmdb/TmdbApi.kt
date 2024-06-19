package com.alpha.showcase.api.tmdb

import com.alpha.showcase.api.TMDB_API_KEY
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

private const val TMDB_ENDPOINT = "https://api.themoviedb.org/3/"
private const val TMDB_ENDPOINT_PROXY = "https://api.tmdb.org/3/"
const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"

private const val TMDB_API_TOKEN = TMDB_API_KEY

class TmdbApi(apiToken: String = TMDB_API_TOKEN) {
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
                "Bearer $apiToken"
            )
        }

    }

    suspend fun getTopRatedMovies(
        page: Int = 1,
        region: String = "US",
        language: String = "en-US"
    ): MovieListResponse {
        return get("movie/top_rated") {
            url {
                parameters.append("page", page.toString())
                parameters.append("region", region)
                parameters.append("language", language)
            }
        }
    }


    suspend fun getPopularMovies(
        page: Int = 1,
        region: String = "US",
        language: String = "en-US"
    ): MovieListResponse {
        return get("movie/popular") {
            url {
                parameters.append("page", page.toString())
                parameters.append("region", region)
                parameters.append("language", language)
            }
        }
    }

    suspend fun getUpcomingMovies(
        page: Int = 1,
        region: String = "US",
        language: String = "en-US"
    ): MovieListResponse {
        return get("movie/upcoming") {
            url {
                parameters.append("page", page.toString())
                parameters.append("region", region)
                parameters.append("language", language)
            }
        }
    }


    suspend fun getNowPlayingMovies(
        page: Int = 1,
        region: String = "US",
        language: String = "en-US"
    ): MovieListResponse {
        return get("movie/now_playing") {
            url {
                parameters.append("page", page.toString())
                parameters.append("region", region)
                parameters.append("language", language)
            }
        }
    }

    suspend fun getMovieImages(
        movieId: Long
    ): MovieImagesResponse {
        return get("movie/$movieId/images")
    }

    private suspend inline fun <reified T> get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return client.get(TMDB_ENDPOINT_PROXY + path, block).body()
    }
}