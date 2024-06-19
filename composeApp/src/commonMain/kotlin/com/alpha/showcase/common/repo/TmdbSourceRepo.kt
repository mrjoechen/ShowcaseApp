package com.alpha.showcase.common.repo

import com.alpha.showcase.api.tmdb.Movie
import com.alpha.showcase.api.tmdb.TMDB_IMAGE_BASE_URL
import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.common.networkfile.storage.external.TMDBSource


class TmdbSourceRepo : SourceRepository<TMDBSource, String> {

    private val tmdbService by lazy {
        TmdbApi()
    }

    override suspend fun getItem(remoteApi: TMDBSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: TMDBSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {
        return try {

            val language = remoteApi.language ?: Language.ENGLISH_US.value
            val region = remoteApi.region ?: Region.US.value
            val imageType = remoteApi.imageType ?: ImageType.POSTER.value
            val content = when (remoteApi.contentType) {
                TOP_RATED_MOVIES -> tmdbService.getTopRatedMovies(
                    language = language,
                    region = region
                )

                POPULAR_MOVIES -> tmdbService.getPopularMovies(language = language, region = region)
                UPCOMING_MOVIES -> tmdbService.getUpcomingMovies(
                    language = language,
                    region = region
                )

                NOW_PLAYING_MOVIES -> tmdbService.getNowPlayingMovies(
                    language = language,
                    region = region
                )

                else -> tmdbService.getNowPlayingMovies(language = language, region = region)
            }

            if (content.results.isEmpty()) {
                Result.failure(Exception("No content found."))
            } else {
                val result = mutableListOf<Movie>()
                result.addAll(content.results)
                if (content.page < content.totalPages) {
                    val nextPage = content.page + 1
                    val nextContent = when (remoteApi.contentType) {
                        TOP_RATED_MOVIES -> tmdbService.getTopRatedMovies(
                            page = nextPage,
                            language = language,
                            region = region
                        )

                        POPULAR_MOVIES -> tmdbService.getPopularMovies(
                            page = nextPage,
                            language = language,
                            region = region
                        )

                        UPCOMING_MOVIES -> tmdbService.getUpcomingMovies(
                            page = nextPage,
                            language = language,
                            region = region
                        )

                        NOW_PLAYING_MOVIES -> tmdbService.getNowPlayingMovies(
                            page = nextPage,
                            language = language,
                            region = region
                        )

                        else -> tmdbService.getNowPlayingMovies(
                            page = nextPage,
                            language = language,
                            region = region
                        )
                    }

                    if (nextContent.results.isNotEmpty()) {
                        result.addAll(nextContent.results)
                    }
                }

                Result.success(result.filter {
                    val imagePath = if (imageType == ImageType.POSTER.value) it.posterPath else it.backdropPath
                    !imagePath.isNullOrEmpty()
                }.map {
                    TMDB_IMAGE_BASE_URL + if (imageType == ImageType.POSTER.value) it.posterPath else it.backdropPath
                })
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

}
