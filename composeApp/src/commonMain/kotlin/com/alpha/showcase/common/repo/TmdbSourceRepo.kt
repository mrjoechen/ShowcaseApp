package com.alpha.showcase.common.repo

import com.alpha.showcase.api.tmdb.Movie
import com.alpha.showcase.api.tmdb.MovieListResponse
import com.alpha.showcase.api.tmdb.TMDB_IMAGE_BASE_URL
import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import kotlinx.coroutines.yield


typealias TmdbPageLoader = suspend (TMDBSource, Int) -> MovieListResponse

class TmdbSourceRepo(
    private val pageLoader: TmdbPageLoader? = null,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : SourceRepository<TMDBSource, String>,
    BatchSourceRepository<TMDBSource, NetworkFile> {

    companion object {
        private const val DEFAULT_MAX_PAGES = 100
    }

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

            val imageType = remoteApi.imageType ?: ImageType.POSTER.value
            val content = loadPage(remoteApi, page = 1)

            if (content.results.isEmpty()) {
                Result.failure(Exception("No content found."))
            } else {
                val result = mutableListOf<Movie>()
                result.addAll(content.results)
                if (content.page < content.totalPages) {
                    val nextContent = loadPage(remoteApi, page = content.page + 1)

                    if (nextContent.results.isNotEmpty()) {
                        result.addAll(nextContent.results.filter {!it.posterPath.isNullOrBlank() && it.overview.isNotBlank()})
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

    override suspend fun streamItems(
        remoteApi: TMDBSource,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Result<Long> {
        return try {
            val effectiveBatchSize = batchSize.coerceAtLeast(1)
            val imageType = remoteApi.imageType ?: ImageType.POSTER.value
            var total = 0L
            var pending = mutableListOf<NetworkFile>()
            var totalPages = maxPages

            for (page in 1..maxPages) {
                if (page > totalPages) {
                    break
                }

                val content = loadPage(remoteApi, page)
                totalPages = content.totalPages.coerceAtMost(maxPages)
                if (content.results.isEmpty()) {
                    break
                }

                content.results.mapNotNull { it.toNetworkFile(remoteApi, imageType) }
                    .filter { filter?.invoke(it) ?: true }
                    .forEach { item ->
                        pending += item
                        total += 1
                        if (pending.size >= effectiveBatchSize) {
                            onBatch(pending)
                            pending = mutableListOf()
                            yield()
                        }
                    }
            }

            if (pending.isNotEmpty()) {
                onBatch(pending)
            }

            Result.success(total)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun loadPage(remoteApi: TMDBSource, page: Int): MovieListResponse {
        pageLoader?.let {
            return it(remoteApi, page)
        }

        val language = remoteApi.language ?: Language.ENGLISH_US.value
        val region = remoteApi.region ?: Region.US.value
        return when (remoteApi.contentType) {
            TOP_RATED_MOVIES -> tmdbService.getTopRatedMovies(page = page, language = language, region = region)
            POPULAR_MOVIES -> tmdbService.getPopularMovies(page = page, language = language, region = region)
            UPCOMING_MOVIES -> tmdbService.getUpcomingMovies(page = page, language = language, region = region)
            NOW_PLAYING_MOVIES -> tmdbService.getNowPlayingMovies(page = page, language = language, region = region)
            else -> tmdbService.getNowPlayingMovies(page = page, language = language, region = region)
        }
    }

    private fun Movie.toNetworkFile(remoteApi: TMDBSource, imageType: String): NetworkFile? {
        val imagePath = if (imageType == ImageType.POSTER.value) posterPath else backdropPath
        if (imagePath.isNullOrBlank()) return null
        return NetworkFile(
            remote = remoteApi,
            path = TMDB_IMAGE_BASE_URL + imagePath,
            fileName = "$id.jpg",
            isDirectory = false,
            size = 0L,
            mimeType = "image/jpeg",
            modTime = releaseDate,
        )
    }

}
