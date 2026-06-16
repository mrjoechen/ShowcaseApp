package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.Pagination
import com.alpha.showcase.api.pexels.Photo as PexelsPhoto
import com.alpha.showcase.api.pexels.Src
import com.alpha.showcase.api.tmdb.Movie
import com.alpha.showcase.api.tmdb.MovieListResponse
import com.alpha.showcase.api.tmdb.TMDB_IMAGE_BASE_URL
import com.alpha.showcase.api.unsplash.Photo as UnsplashPhoto
import com.alpha.showcase.api.unsplash.PhotoUrls
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalImageSourceRepoTest {

    @Test
    fun unsplashStreamItemsLoadsPagesUntilEmptyPage() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = UnsplashRepo(
            pageLoader = { _, page, _ ->
                requestedPages += page
                when (page) {
                    1 -> listOf(unsplashPhoto("one"), unsplashPhoto("two"))
                    2 -> listOf(unsplashPhoto("three"))
                    else -> emptyList()
                }
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
            batchSize = 2,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertEquals(Result.success(3L), result)
        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(
            listOf(
                listOf("https://images.example/one.jpg", "https://images.example/two.jpg"),
                listOf("https://images.example/three.jpg"),
            ),
            batches,
        )
    }

    @Test
    fun pexelsStreamItemsLoadsPagesUntilNextPageIsMissing() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                requestedPages += page
                when (page) {
                    1 -> pexelsPage(
                        page = 1,
                        perPage = perPage,
                        nextPage = "https://api.pexels.com/v1/curated?page=2",
                        photos = listOf(pexelsPhoto("one"), pexelsPhoto("two")),
                    )
                    2 -> pexelsPage(
                        page = 2,
                        perPage = perPage,
                        nextPage = null,
                        photos = listOf(pexelsPhoto("three")),
                    )
                    else -> pexelsPage(page = page, perPage = perPage, nextPage = null, photos = emptyList())
                }
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
            batchSize = 2,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertEquals(Result.success(3L), result)
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(
            listOf(
                listOf("https://images.example/one.jpg", "https://images.example/two.jpg"),
                listOf("https://images.example/three.jpg"),
            ),
            batches,
        )
    }

    @Test
    fun tmdbStreamItemsLoadsPagesUntilTotalPages() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = TmdbSourceRepo(
            pageLoader = { _, page ->
                requestedPages += page
                MovieListResponse(
                    page = page,
                    totalPages = 3,
                    totalResults = 3,
                    results = listOf(tmdbMovie(page, "/poster-$page.jpg")),
                )
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = TMDBSource(
                name = "Popular",
                contentType = POPULAR_MOVIES,
                language = Language.ENGLISH_US.value,
                region = Region.US.value,
                imageType = ImageType.POSTER.value,
            ),
            batchSize = 2,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertEquals(Result.success(3L), result)
        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(
            listOf(
                listOf("${TMDB_IMAGE_BASE_URL}/poster-1.jpg", "${TMDB_IMAGE_BASE_URL}/poster-2.jpg"),
                listOf("${TMDB_IMAGE_BASE_URL}/poster-3.jpg"),
            ),
            batches,
        )
    }

    private fun unsplashPhoto(id: String): UnsplashPhoto {
        return UnsplashPhoto(
            id = id,
            width = 1600,
            height = 900,
            description = null,
            urls = PhotoUrls(
                raw = "https://images.example/$id.raw",
                full = "https://images.example/$id.full",
                regular = "https://images.example/$id.jpg",
                small = "https://images.example/$id-small.jpg",
                thumb = "https://images.example/$id-thumb.jpg",
            ),
        )
    }

    private fun pexelsPage(
        page: Int,
        perPage: Int,
        nextPage: String?,
        photos: List<PexelsPhoto>,
    ): Pagination {
        return Pagination(
            nextPage = nextPage,
            page = page,
            perPage = perPage,
            photos = photos,
        )
    }

    private fun pexelsPhoto(id: String): PexelsPhoto {
        return PexelsPhoto(
            alt = id,
            avgColor = "#000000",
            height = 900,
            id = id.hashCode(),
            liked = false,
            photographer = "tester",
            photographerId = 1L,
            photographerUrl = "https://example.com/tester",
            src = Src(
                landscape = "https://images.example/$id-landscape.jpg",
                large = "https://images.example/$id-large.jpg",
                large2x = "https://images.example/$id-large2x.jpg",
                medium = "https://images.example/$id-medium.jpg",
                original = "https://images.example/$id.jpg",
                portrait = "https://images.example/$id-portrait.jpg",
                small = "https://images.example/$id-small.jpg",
                tiny = "https://images.example/$id-tiny.jpg",
            ),
            url = "https://pexels.example/$id",
            width = 1600,
        )
    }

    private fun tmdbMovie(id: Int, posterPath: String): Movie {
        return Movie(
            id = id,
            title = "Movie $id",
            overview = "Overview $id",
            posterPath = posterPath,
            backdropPath = "/backdrop-$id.jpg",
            voteAverage = 7.0,
            releaseDate = "2024-01-0$id",
        )
    }
}
