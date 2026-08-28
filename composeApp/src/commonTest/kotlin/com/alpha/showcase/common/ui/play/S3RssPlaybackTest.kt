package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.repo.S3ObjectUrlSigner
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import com.alpha.showcase.common.repo.createS3ObjectUrlSigner
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class S3RssPlaybackTest {

    @Test
    fun s3PlaybackIsResolvedBeforeItReachesCompose() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val s3File = networkFile(s3, "album/cat.jpg").copy(
            extra = mapOf("s3Key" to "album/cat.jpg", "etag" to "cat-v1"),
        )
        var signingCount = 0
        var resolverCount = 0
        var signedObjectKey: String? = null

        val playback = assertIs<DataWithType>(
            convertNetworkFilesForPlayback(
                api = s3,
                files = listOf(s3File),
                resolveS3Signer = { source ->
                    assertSame(s3, source)
                    resolverCount += 1
                    S3ObjectUrlSigner { objectKey ->
                        signingCount += 1
                        signedObjectKey = objectKey
                        signed("https://signed.example/$objectKey?signature=rotating")
                    }
                },
            ).single(),
        )
        val resolved = assertIs<ResolvedImageModel>(playback.data)

        assertEquals(1, resolverCount)
        assertEquals(1, signingCount)
        assertEquals("album/cat.jpg", signedObjectKey)
        assertEquals(
            "https://signed.example/album/cat.jpg?signature=rotating",
            resolved.resolveForFetch(0L).urlForFetch(),
        )
        assertEquals(s3File.path, resolved.stableKey)
        assertEquals(s3NetworkFileCacheKey(s3File), resolved.cacheKey)
        assertTrue(resolved.cacheKey.startsWith("s3:"))
        assertFalse(resolved.cacheKey.contains("signature"))
        assertEquals("album/cat.jpg", stableMediaKey(playback))
    }

    @Test
    fun signedUrlRotationDoesNotChangeS3CacheOrPagerIdentity() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val file = networkFile(s3, "album/cat.jpg").copy(
            extra = mapOf("s3Key" to "album/cat.jpg", "etag" to "cat-v1"),
        )
        var signature = 0

        suspend fun resolve(): DataWithType = assertIs(
            convertNetworkFilesForPlayback(s3, listOf(file)) {
                S3ObjectUrlSigner { objectKey ->
                    signed("https://signed.example/$objectKey?signature=${++signature}")
                }
            }.single(),
        )

        val first = resolve()
        val second = resolve()
        val firstModel = assertIs<ResolvedImageModel>(first.data)
        val secondModel = assertIs<ResolvedImageModel>(second.data)

        assertNotEquals(
            firstModel.resolveForFetch(0L).urlForFetch(),
            secondModel.resolveForFetch(0L).urlForFetch(),
        )
        assertEquals(firstModel.cacheKey, secondModel.cacheKey)
        assertEquals(stableMediaKey(first), stableMediaKey(second))
        assertEquals(listOf("album/cat.jpg"), cachePathCandidates(s3, first))
        assertEquals(listOf("album/cat.jpg"), cachePathCandidates(s3, second))
    }

    @Test
    fun s3BatchResolvesOneSignerAndSignsEveryObjectInInputOrder() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val files = listOf(
            networkFile(s3, "cache/first.jpg").copy(
                extra = mapOf("s3Key" to "objects/first.jpg", "etag" to "first-v1"),
            ),
            networkFile(s3, "cache/second.png").copy(
                mimeType = "image/png",
                extra = mapOf("s3Key" to "objects/second.png", "etag" to "second-v1"),
            ),
        )
        var resolverCount = 0
        val signedKeys = mutableListOf<String>()

        val playback = convertNetworkFilesForPlayback(
            api = s3,
            files = files,
            resolveS3Signer = {
                resolverCount += 1
                S3ObjectUrlSigner { objectKey ->
                    signedKeys += objectKey
                    signed("https://signed.example/$objectKey")
                }
            },
        ).map { assertIs<DataWithType>(it) }

        assertEquals(1, resolverCount)
        assertEquals(listOf("objects/first.jpg", "objects/second.png"), signedKeys)
        assertEquals(listOf("image/jpeg", "image/png"), playback.map { it.type })
        assertEquals(
            listOf("cache/first.jpg", "cache/second.png"),
            playback.map { assertIs<ResolvedImageModel>(it.data).stableKey },
        )
        assertEquals(
            listOf("https://signed.example/objects/first.jpg", "https://signed.example/objects/second.png"),
            playback.map { assertIs<ResolvedImageModel>(it.data).resolveForFetch(0L).urlForFetch() },
        )
        assertEquals(listOf("cache/first.jpg"), cachePathCandidates(s3, playback.first()))
    }

    @Test
    fun largeS3BatchYieldsToSiblingWithoutChangingSigningOrder() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val files = (0 until 200).map { index ->
            networkFile(s3, "cache/$index.jpg").copy(
                extra = mapOf("s3Key" to "objects/$index.jpg"),
            )
        }
        val signingDispatcher = StandardTestDispatcher(testScheduler)
        val signedKeys = mutableListOf<String>()
        var resolverCount = 0
        var siblingObservedSignedCount = -1

        val conversion = async(signingDispatcher) {
            convertNetworkFilesForPlayback(
                api = s3,
                files = files,
                signingDispatcher = signingDispatcher,
                resolveS3Signer = {
                    resolverCount += 1
                    S3ObjectUrlSigner { objectKey ->
                        signedKeys += objectKey
                        signed("https://signed.example/$objectKey")
                    }
                },
            )
        }
        val sibling = launch(signingDispatcher) {
            while (signedKeys.isEmpty()) yield()
            siblingObservedSignedCount = signedKeys.size
        }

        testScheduler.advanceUntilIdle()

        assertEquals(200, conversion.await().size)
        sibling.join()
        assertEquals(1, resolverCount)
        assertEquals((0 until 200).map { "objects/$it.jpg" }, signedKeys)
        assertTrue(siblingObservedSignedCount in 1 until 200)
    }

    @Test
    fun largeS3BatchCanBeCancelledBeforeAllSigningCompletes() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val files = (0 until 200).map { index -> networkFile(s3, "objects/$index.jpg") }
        val signingDispatcher = StandardTestDispatcher(testScheduler)
        var signingCount = 0

        val conversion = async(signingDispatcher) {
            convertNetworkFilesForPlayback(
                api = s3,
                files = files,
                signingDispatcher = signingDispatcher,
                resolveS3Signer = {
                    S3ObjectUrlSigner { objectKey ->
                        signingCount += 1
                        signed("https://signed.example/$objectKey")
                    }
                },
            )
        }
        launch(signingDispatcher) {
            while (signingCount < 25) yield()
            conversion.cancel()
        }

        testScheduler.advanceUntilIdle()

        assertFailsWith<CancellationException> { conversion.await() }
        assertTrue(signingCount in 25 until 200)
    }

    @Test
    fun s3SignerContextDecryptsOnlyOnceBeforeSigningManyKeys() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "encrypted-secret", "photos", "us-east-1")
        var decryptCount = 0

        val signer = createS3ObjectUrlSigner(
            source = s3,
            decryptSecret = { encrypted ->
                decryptCount += 1
                assertEquals("encrypted-secret", encrypted)
                "plain-secret"
            },
        )
        val signed = listOf(signer.sign("one.jpg"), signer.sign("two.jpg"), signer.sign("three.jpg"))

        assertEquals(1, decryptCount)
        assertEquals(3, signed.size)
        assertTrue(signed[0].urlForFetch().contains("one.jpg"))
        assertTrue(signed[1].urlForFetch().contains("two.jpg"))
        assertTrue(signed[2].urlForFetch().contains("three.jpg"))
    }

    @Test
    fun s3SignerUsesExplicitOneHourLifetimeAndConservativeInjectedClockExpiry() = runTest {
        val now = 1_780_000_000_123L
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "encrypted-secret", "photos", "us-east-1")
        val signer = createS3ObjectUrlSigner(
            source = s3,
            decryptSecret = { "plain-secret" },
            clockMillis = { now },
        )

        val signed = signer.sign("album/cat.jpg")

        assertEquals(now + 3_540_000L, signed.expiresAtEpochMillis)
        assertTrue(signed.urlForFetch().contains("X-Amz-Expires=3600"))
        assertFalse(signed.toString().contains("X-Amz-Credential"))
    }

    @Test
    fun resolvedS3ModelUsesStablePathForCacheLookup() {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val signed = signed("https://signed.example/cat.jpg?signature=rotating")
        val playback = DataWithType(
            data = ResolvedImageModel(
                initialSignedRequest = signed,
                stableKey = "album/cat.jpg",
                cacheKey = "s3:stable-cache-key",
                refreshSignedRequest = { signed },
            ),
            type = "image/jpeg",
        )

        assertEquals(
            listOf("album/cat.jpg"),
            cachePathCandidates(s3, playback),
            "The DB lookup must use the durable cache path, never the expiring signed URL",
        )
    }

    @Test
    fun resolvedModelToStringRedactsRequestDataAndHeaderValues() {
        val signedUrl =
            "https://signed.example/album/cat.jpg?X-Amz-Credential=AKIA_SECRET&X-Amz-Signature=query-secret"
        val headerSecret = "Bearer header-secret"
        val signed = signed(signedUrl)
        val model = ResolvedImageModel(
            initialSignedRequest = signed,
            stableKey = "album/cat.jpg",
            cacheKey = "s3:opaque-cache-key",
            headers = mapOf("Authorization" to headerSecret),
            refreshSignedRequest = { signed },
        )

        listOf(model.toString(), DataWithType(model, "image/jpeg").toString()).forEach { rendered ->
            assertFalse(rendered.contains(signedUrl))
            assertFalse(rendered.contains("AKIA_SECRET"))
            assertFalse(rendered.contains("query-secret"))
            assertFalse(rendered.contains(headerSecret))
        }
    }

    @Test
    fun rssPlaybackUsesItsRemoteUrl() = runTest {
        val rss = RssSource("News", "https://example.com/feed.xml")

        assertEquals(
            listOf("https://cdn.example.com/feed.jpg"),
            convertNetworkFilesForPlayback(
                api = rss,
                files = listOf(networkFile(rss, "https://cdn.example.com/feed.jpg")),
                resolveS3Signer = { error("RSS playback must not resolve an S3 signer") },
            ),
        )
    }

    @Test
    fun s3SignerFailurePropagatesUnchanged() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val failure = IllegalStateException("signing failed")

        val thrown = assertFailsWith<IllegalStateException> {
            convertNetworkFilesForPlayback(s3, listOf(networkFile(s3, "album/cat.jpg"))) {
                S3ObjectUrlSigner { throw failure }
            }
        }

        assertSame(failure, thrown)
    }

    @Test
    fun s3SignerCancellationPropagatesUnchanged() = runTest {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val cancellation = CancellationException("signing cancelled")

        val thrown = assertFailsWith<CancellationException> {
            convertNetworkFilesForPlayback(
                api = s3,
                files = listOf(networkFile(s3, "album/cat.jpg")),
                resolveS3Signer = { throw cancellation },
            )
        }

        assertSame(cancellation, thrown)
    }

    private fun networkFile(
        source: com.alpha.showcase.common.networkfile.storage.remote.RemoteApi,
        path: String,
    ) = NetworkFile(
        remote = source,
        path = path,
        fileName = path.substringAfterLast('/'),
        isDirectory = false,
        size = 0L,
        mimeType = "image/jpeg",
        modTime = "",
    )

    private fun signed(url: String, expiresAtEpochMillis: Long = Long.MAX_VALUE) =
        SignedS3ObjectUrl(url, expiresAtEpochMillis)
}
