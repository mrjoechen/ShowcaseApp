package com.alpha.showcase.api.tmdb

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.builtInTmdbApiToken
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

private const val TMDB_ENDPOINT = "https://api.themoviedb.org/3/"
private const val TMDB_ENDPOINT_PROXY = "https://api.tmdb.org/3/"
const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"

internal fun HeadersBuilder.applyTmdbApiToken(apiToken: String) {
    append(HttpHeaders.Authorization, "Bearer $apiToken")
}

class TmdbApi(private val apiToken: String = builtInTmdbApiToken()) : BaseHttpClient() {
    
    override fun configureClient(config: io.ktor.client.HttpClientConfig<*>) {
        config.defaultRequest {
            headers.applyTmdbApiToken(apiToken)
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
