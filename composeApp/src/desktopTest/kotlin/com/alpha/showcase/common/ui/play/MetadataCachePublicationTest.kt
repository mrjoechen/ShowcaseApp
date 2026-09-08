package com.alpha.showcase.common.ui.play

import coil3.PlatformContext
import coil3.asImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MetadataCachePublicationTest {
    @Test fun aConcurrentReaderMissesUntilPixelsAndMetadataArePublishedTogether() {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(80, 40)
            val image = bitmap.asImage()
            val cache = cache()
            val key = metadataKey()
            // Engine's initial write and a second request's read interleave before attach.
            cache[key] = MemoryCache.Value(image, mapOf("coil#is_sampled" to true))
            assertNull(cache[key])

            val metadata = MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "Published photo")))
            cache.attach(key, image, metadata)
            val published = assertNotNull(cache[key])
            assertSame(image, published.image)
            assertEquals(true, published.extras["coil#is_sampled"])
            assertEquals(metadata, cache.metadata(key, published.image))
        }
    }

    @Test fun missingMetadataStillPublishesTheImageForSubsequentMemoryHits() {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(80, 40)
            val image = bitmap.asImage()
            val cache = cache()
            val key = metadataKey()
            cache[key] = MemoryCache.Value(image)
            assertNull(cache[key])

            cache.attach(key, image, null)
            assertSame(image, assertNotNull(cache[key]).image)
            assertNull(cache.metadata(key, image))
        }
    }

    @Test fun anOlderRequestCannotPublishOrReplaceANewerRequestsPixels() {
        Bitmap().use { oldBitmap ->
            Bitmap().use { newBitmap ->
                oldBitmap.allocN32Pixels(80, 40)
                newBitmap.allocN32Pixels(80, 40)
                val oldImage = oldBitmap.asImage()
                val newImage = newBitmap.asImage()
                val cache = cache()
                val key = metadataKey()

                cache[key] = MemoryCache.Value(oldImage)
                cache[key] = MemoryCache.Value(newImage)
                cache.attach(key, oldImage, MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "Old photo"))))
                assertNull(cache[key])

                val newMetadata = MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "New photo")))
                cache.attach(key, newImage, newMetadata)
                cache.attach(key, oldImage, MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "Late old photo"))))
                val published = assertNotNull(cache[key])
                assertSame(newImage, published.image)
                assertEquals(newMetadata, cache.metadata(key, published.image))
            }
        }
    }

    @Test fun ordinaryImageRequestsRemainReadableImmediatelyWithoutPublication() {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(80, 40)
            val image = bitmap.asImage()
            val cache = cache()
            val key = MemoryCache.Key("ordinary-thumbnail")
            cache[key] = MemoryCache.Value(image)
            assertSame(image, assertNotNull(cache[key]).image)
        }
    }

    private fun cache() = MetadataMemoryCache(
        MemoryCache.Builder().maxSizeBytes(1024 * 1024).weakReferencesEnabled(false).build(),
    )

    private fun metadataKey(): MemoryCache.Key {
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data("photo.png")
            .withMediaMetadata().build()
        return MemoryCache.Key("photo", request.memoryCacheKeyExtras)
    }
}
