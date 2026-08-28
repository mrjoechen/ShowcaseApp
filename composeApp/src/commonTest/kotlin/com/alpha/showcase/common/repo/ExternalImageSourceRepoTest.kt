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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun unsplashStreamItemsEmitsSuccessfulPagesButReportsLaterPageFailure() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = UnsplashRepo(
            pageLoader = { _, page, _ ->
                requestedPages += page
                when (page) {
                    1 -> listOf(unsplashPhoto("one"), unsplashPhoto("two"))
                    else -> error("page $page failed")
                }
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
            batchSize = 200,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertTrue(result.isFailure)
        assertEquals("page 2 failed", result.exceptionOrNull()?.message)
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(
            listOf(listOf("https://images.example/one.jpg", "https://images.example/two.jpg")),
            batches,
        )
    }

    @Test
    fun unsplashGetItemsUsesPaginatedStream() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = UnsplashRepo(
            pageLoader = { _, page, _ ->
                requestedPages += page
                when (page) {
                    1 -> listOf(unsplashPhoto("one"))
                    2 -> listOf(unsplashPhoto("two"))
                    else -> emptyList()
                }
            },
            maxPages = 10,
        )

        val result = repo.getItems(
            remoteApi = UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
        )

        assertEquals(
            listOf("https://images.example/one.jpg", "https://images.example/two.jpg"),
            result.getOrThrow().map { it.data },
        )
        assertEquals(listOf(1, 2, 3), requestedPages)
    }

    @Test
    fun unsplashConnectionProbeRequestsOnlyFirstPage() = runTest {
        val requestedPages = mutableListOf<Pair<Int, Int>>()
        val repo = UnsplashRepo(
            pageLoader = { _, page, perPage ->
                requestedPages += page to perPage
                when (page) {
                    1 -> listOf(unsplashPhoto("one"))
                    else -> error("page $page failed")
                }
            },
            maxPages = 10,
        )

        val result = RepoManager(unSplashSourceRepo = repo).checkConnection(
            UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
        )

        assertEquals(listOf(1 to 30), requestedPages)
        assertTrue(result.isSuccess)
    }

    @Test
    fun unsplashConnectionProbeRethrowsIdenticalCancellation() = runTest {
        val cancellation = CancellationException("unsplash probe cancelled")
        val repo = UnsplashRepo(
            pageLoader = { _, _, _ -> throw cancellation },
        )

        val thrown = assertFailsWith<CancellationException> {
            repo.checkConnection(
                UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
            )
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun unsplashConnectionProbeDoesNotResolveDefaultCacheService() = runTest {
        var defaultCacheServiceResolutions = 0
        val repo = UnsplashRepo(
            pageLoader = { _, _, _ -> listOf(unsplashPhoto("one")) },
        )
        val manager = RepoManager(
            unSplashSourceRepo = repo,
            defaultCacheServiceProvider = {
                defaultCacheServiceResolutions += 1
                error("connection probe must not resolve the default cache service")
            },
        )

        val result = manager.checkConnection(
            UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, defaultCacheServiceResolutions)
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
    fun pexelsStreamItemsEmitsSuccessfulPagesButReportsLaterPageFailure() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                requestedPages += page
                when (page) {
                    1 -> pexelsPage(
                        page = page,
                        perPage = perPage,
                        nextPage = "https://api.pexels.com/v1/curated?page=2",
                        photos = listOf(pexelsPhoto("one"), pexelsPhoto("two")),
                    )
                    else -> error("page $page failed")
                }
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
            batchSize = 200,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertTrue(result.isFailure)
        assertEquals("page 2 failed", result.exceptionOrNull()?.message)
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(
            listOf(listOf("https://images.example/one.jpg", "https://images.example/two.jpg")),
            batches,
        )
    }

    @Test
    fun pexelsGetItemsUsesPaginatedStream() = runTest {
        val requestedPages = mutableListOf<Int>()
        val repo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                requestedPages += page
                pexelsPage(
                    page = page,
                    perPage = perPage,
                    nextPage = if (page == 1) "https://api.pexels.com/v1/curated?page=2" else null,
                    photos = listOf(pexelsPhoto(if (page == 1) "one" else "two")),
                )
            },
            maxPages = 10,
        )

        val result = repo.getItems(
            remoteApi = PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
        )

        assertEquals(
            listOf("https://images.example/one.jpg", "https://images.example/two.jpg"),
            result.getOrThrow(),
        )
        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun pexelsConnectionProbeRequestsOnlyFirstPage() = runTest {
        val requestedPages = mutableListOf<Pair<Int, Int>>()
        val repo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                requestedPages += page to perPage
                when (page) {
                    1 -> pexelsPage(
                        page = page,
                        perPage = perPage,
                        nextPage = "https://api.pexels.com/v1/curated?page=2",
                        photos = listOf(pexelsPhoto("one")),
                    )

                    else -> error("page $page failed")
                }
            },
            maxPages = 10,
        )

        val result = RepoManager(pexelsSourceRepo = repo).checkConnection(
            PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
        )

        assertEquals(listOf(1 to 80), requestedPages)
        assertTrue(result.isSuccess)
    }

    @Test
    fun pexelsConnectionProbeCancellationPropagatesThroughRepoManager() = runTest {
        val cancellation = CancellationException("pexels probe cancelled")
        val repo = PexelsSourceRepo(
            pageLoader = { _, _, _ -> throw cancellation },
        )

        val thrown = assertFailsWith<CancellationException> {
            RepoManager(pexelsSourceRepo = repo).checkConnection(
                PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
            )
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun externalConnectionProbesAcceptSuccessfulEmptyFirstPages() = runTest {
        val unsplashRepo = UnsplashRepo(
            pageLoader = { _, _, _ -> emptyList() },
        )
        val pexelsRepo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                pexelsPage(
                    page = page,
                    perPage = perPage,
                    nextPage = null,
                    photos = emptyList(),
                )
            },
        )

        val unsplashResult = unsplashRepo.checkConnection(
            UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
        )
        val pexelsResult = pexelsRepo.checkConnection(
            PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
        )

        assertEquals(Result.success(Unit), unsplashResult)
        assertEquals(Result.success(Unit), pexelsResult)
    }

    @Test
    fun externalConnectionProbesReportOrdinaryFirstPageFailures() = runTest {
        val unsplashFailure = IllegalStateException("unsplash unavailable")
        val pexelsFailure = IllegalArgumentException("pexels credentials rejected")
        val unsplashRepo = UnsplashRepo(
            pageLoader = { _, _, _ -> throw unsplashFailure },
        )
        val pexelsRepo = PexelsSourceRepo(
            pageLoader = { _, _, _ -> throw pexelsFailure },
        )

        val unsplashResult = unsplashRepo.checkConnection(
            UnSplashSource("Wallpapers", UnSplashSourceType.FeedPhotos.type),
        )
        val pexelsResult = pexelsRepo.checkConnection(
            PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
        )

        assertSame(unsplashFailure, unsplashResult.exceptionOrNull())
        assertSame(pexelsFailure, pexelsResult.exceptionOrNull())
    }

    @Test
    fun pexelsStreamItemsCapsRemotePageSizeWhileRetainingOutputBatchSize() = runTest {
        val requestedPerPages = mutableListOf<Int>()
        val repo = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                requestedPerPages += perPage
                val photoCount = when (page) {
                    1, 2 -> 80
                    3 -> 40
                    else -> 0
                }
                pexelsPage(
                    page = page,
                    perPage = perPage,
                    nextPage = if (page < 3) "https://api.pexels.com/v1/curated?page=${page + 1}" else null,
                    photos = (1..photoCount).map { pexelsPhoto("page-$page-photo-$it") },
                )
            },
            maxPages = 10,
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = PexelsSource("Curated", PexelsSourceType.FeedPhotos.type),
            batchSize = 200,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertEquals(Result.success(200L), result)
        assertEquals(listOf(80, 80, 80), requestedPerPages)
        assertEquals(listOf(200), batches.map { it.size })
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
            createdAt = null,
            updatedAt = null,
            promotedAt = null,
            altDescription = null,
            color = null,
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
