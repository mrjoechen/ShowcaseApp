package com.alpha.showcase.common.repo

import com.alpha.showcase.api.s3.S3ListPage
import com.alpha.showcase.api.s3.S3ObjectItem
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S3RssSourceRepoTest {

    @Test
    fun s3StreamingKeepsStableObjectKeysWhileFollowingContinuationTokens() = runTest {
        val requestedTokens = mutableListOf<String?>()
        val source = S3Source(
            name = "Archive",
            endpoint = "s3.amazonaws.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
            region = "us-east-1",
        )
        val repo = S3SourceRepo(
            pageLoader = { _, _, token ->
                requestedTokens += token
                when (token) {
                    null -> S3ListPage(
                        objects = listOf(
                            s3Object("cat.jpg"),
                            s3Object("notes.txt"),
                        ),
                        commonPrefixes = listOf("holidays/"),
                        isTruncated = true,
                        nextContinuationToken = "page-2",
                    )
                    "page-2" -> S3ListPage(
                        objects = listOf(s3Object("dog.png")),
                        commonPrefixes = emptyList(),
                        isTruncated = false,
                        nextContinuationToken = null,
                    )
                    else -> error("Unexpected token: $token")
                }
            },
        )
        val batches = mutableListOf<List<String>>()

        val result = repo.streamItems(
            remoteApi = source,
            recursive = true,
            filter = { file -> file.mimeType.startsWith("image/") },
            batchSize = 2,
        ) { batch ->
            batches += batch.map { it.path }
        }

        assertEquals(Result.success(2L), result)
        assertEquals(listOf(null, "page-2"), requestedTokens)
        assertEquals(
            listOf(listOf("cat.jpg", "dog.png")),
            batches,
        )
    }

    @Test
    fun rssRepositoryMapsFeedUrlsToPlayableNetworkFilesAndAppliesFilters() = runTest {
        val source = RssSource(name = "News", url = "https://example.com/feed.xml")
        val repo = RssSourceRepo(
            feedLoader = {
                listOf(
                    "https://cdn.example.com/hero.jpg",
                    "https://cdn.example.com/clip.mp4",
                    "https://cdn.example.com/no-extension",
                )
            },
        )

        val result = repo.getItems(source) { file -> file.mimeType.startsWith("image/") }

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("https://cdn.example.com/hero.jpg", "https://cdn.example.com/no-extension"),
            result.getOrThrow().map { it.path },
        )
        assertEquals(
            listOf("image/jpeg", "image/jpeg"),
            result.getOrThrow().map { it.mimeType },
        )
    }

    @Test
    fun repositoryManagerRoutesS3AndRssSourcesToTheirRepositories() = runTest {
        val s3Repo = S3SourceRepo(
            pageLoader = { _, _, _ ->
                S3ListPage(
                    objects = listOf(s3Object("cat.jpg")),
                    commonPrefixes = emptyList(),
                    isTruncated = false,
                    nextContinuationToken = null,
                )
            },
        )
        val rssRepo = RssSourceRepo(
            feedLoader = { listOf("https://cdn.example.com/feed.jpg") },
        )
        val manager = RepoManager(s3SourceRepo = s3Repo, rssSourceRepo = rssRepo)

        val s3Result = manager.getItems(
            S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1"),
        )
        val rssResult = manager.getItems(RssSource("News", "https://example.com/feed.xml"))

        assertEquals("cat.jpg", (s3Result.getOrThrow().single() as com.alpha.showcase.common.networkfile.model.NetworkFile).path)
        assertEquals("https://cdn.example.com/feed.jpg", (rssResult.getOrThrow().single() as com.alpha.showcase.common.networkfile.model.NetworkFile).path)
    }

    private fun s3Object(key: String): S3ObjectItem = S3ObjectItem(
        key = key,
        size = 10L,
        lastModified = "2026-08-20T01:02:03Z",
        etag = "etag-$key",
    )
}
