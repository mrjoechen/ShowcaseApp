package com.alpha.showcase.common.ui.ext

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.ui.play.s3NetworkFileCacheKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ImageLoadExtTest {

    @Test
    fun s3CacheKeyChangesWithEtagAndDoesNotExposeSecret() {
        val source = S3Source(
            name = "Archive",
            endpoint = "s3.amazonaws.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
            region = "us-east-1",
        )
        fun file(etag: String) = NetworkFile(
            remote = source,
            path = "album/cat.jpg",
            fileName = "cat.jpg",
            isDirectory = false,
            size = 10L,
            mimeType = "image/jpeg",
            modTime = "",
            extra = mapOf("s3Key" to "album/cat.jpg", "etag" to etag),
        )

        val oldKey = s3NetworkFileCacheKey(file("etag-old"))
        val newKey = s3NetworkFileCacheKey(file("etag-new"))

        assertNotEquals(oldKey, newKey)
        assertFalse(oldKey.contains(source.secretKey))
        assertFalse(oldKey.contains(source.endpoint))
        assertFalse(oldKey.contains("album/cat.jpg"))
        assertTrue(oldKey.matches(Regex("^s3:[0-9a-f]{64}$")))
    }

    @Test
    fun s3CacheKeyFallsBackToModificationMetadataWhenEtagIsMissing() {
        val source = S3Source(
            name = "Archive",
            endpoint = "storage.example.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
            region = "us-east-1",
        )
        fun file(modTime: String) = NetworkFile(
            remote = source,
            path = "album/cat.jpg",
            fileName = "cat.jpg",
            isDirectory = false,
            size = 10L,
            mimeType = "image/jpeg",
            modTime = modTime,
            extra = mapOf("s3Key" to "album/cat.jpg"),
        )

        val oldKey = s3NetworkFileCacheKey(file("2026-08-20T01:02:03Z"))
        val newKey = s3NetworkFileCacheKey(file("2026-08-21T01:02:03Z"))

        assertNotEquals(oldKey, newKey)
    }

    @Test
    fun s3CacheKeyCanonicalizationRejectsLegacyColonCollisions() {
        val firstSource = source(endpoint = "a:b", region = "c")
        val secondSource = source(endpoint = "a", region = "b:c")
        val firstFile = file(firstSource, path = "stable/cat.jpg", objectKey = "objects/cat.jpg")
        val secondFile = file(secondSource, path = "stable/cat.jpg", objectKey = "objects/cat.jpg")

        assertEquals(legacyCacheKey(firstFile), legacyCacheKey(secondFile))
        assertNotEquals(s3NetworkFileCacheKey(firstFile), s3NetworkFileCacheKey(secondFile))
    }

    @Test
    fun s3CacheKeyUsesObjectKeyRatherThanStablePlaybackPath() {
        val source = source(endpoint = "storage.example.com", region = "us-east-1")
        val first = file(source, path = "stable/cat.jpg", objectKey = "objects/first.jpg")
        val second = file(source, path = "stable/cat.jpg", objectKey = "objects/second.jpg")

        assertNotEquals(s3NetworkFileCacheKey(first), s3NetworkFileCacheKey(second))
    }

    private fun source(endpoint: String, region: String) = S3Source(
        name = "Archive",
        endpoint = endpoint,
        accessKey = "access",
        secretKey = "encrypted-secret",
        bucket = "photos",
        region = region,
    )

    private fun file(source: S3Source, path: String, objectKey: String) = NetworkFile(
        remote = source,
        path = path,
        fileName = path.substringAfterLast('/'),
        isDirectory = false,
        size = 10L,
        mimeType = "image/jpeg",
        modTime = "2026-08-20T01:02:03Z",
        extra = mapOf("s3Key" to objectKey, "etag" to "etag-v1"),
    )

    private fun legacyCacheKey(file: NetworkFile): String {
        val source = file.remote as S3Source
        val objectKey = file.extra?.get("s3Key") ?: file.path
        val version = file.extra?.get("etag")?.takeIf { it.isNotBlank() }
            ?: "${file.modTime}:${file.size}"
        return "s3:${source.useSSL}:${source.endpoint}:${source.region}:${source.bucket}:$objectKey:$version"
    }
}
