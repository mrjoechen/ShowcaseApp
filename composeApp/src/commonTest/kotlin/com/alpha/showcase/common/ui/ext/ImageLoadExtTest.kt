package com.alpha.showcase.common.ui.ext

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ImageLoadExtTest {

    @Test
    fun s3NetworkFileIsResignedForEveryImageRequest() {
        val source = S3Source(
            name = "Archive",
            endpoint = "s3.amazonaws.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
            region = "us-east-1",
        )
        val file = NetworkFile(
            remote = source,
            path = "album/cat.jpg",
            fileName = "cat.jpg",
            isDirectory = false,
            size = 10L,
            mimeType = "image/jpeg",
            modTime = "",
            extra = mapOf("s3Key" to "album/cat.jpg"),
        )
        var signingCount = 0
        val signer: (S3Source, String) -> String = { _, key ->
            "https://signed.example/${++signingCount}/$key"
        }

        val first = resolveS3NetworkFileUrl(file, signer)
        val second = resolveS3NetworkFileUrl(file, signer)

        assertEquals("https://signed.example/1/album/cat.jpg", first)
        assertEquals("https://signed.example/2/album/cat.jpg", second)
        assertEquals(2, signingCount)
    }

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
}
