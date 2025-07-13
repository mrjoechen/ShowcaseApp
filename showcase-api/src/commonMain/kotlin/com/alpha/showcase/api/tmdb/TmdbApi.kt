package com.alpha.showcase.api.tmdb

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.TMDB_API_KEY
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private const val TMDB_ENDPOINT = "https://api.themoviedb.org/3/"
private const val TMDB_ENDPOINT_PROXY = "https://api.tmdb.org/3/"
const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"

private const val TMDB_API_TOKEN = TMDB_API_KEY

class TmdbApi(private val apiToken: String = TMDB_API_TOKEN) : BaseHttpClient() {
    
    override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        config.defaultRequest {
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
        return get(TMDB_ENDPOINT_PROXY + "movie/top_rated") {
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
        return get(TMDB_ENDPOINT_PROXY + "movie/popular") {
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
        return get(TMDB_ENDPOINT_PROXY + "movie/upcoming") {
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
        return get(TMDB_ENDPOINT_PROXY + "movie/now_playing") {
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
        return get(TMDB_ENDPOINT_PROXY + "movie/$movieId/images")
    }
}