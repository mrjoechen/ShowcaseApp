package com.alpha.showcase.common.ui.ext

import coil3.PlatformContext
import coil3.network.httpHeaders
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResolvedImageRequestTest {

    private val initialSigned = SignedS3ObjectUrl(
        "https://signed.example/album/cat.jpg?signature=rotating",
        Long.MAX_VALUE,
    )
    private val model = ResolvedImageModel(
        initialSignedRequest = initialSigned,
        stableKey = "album/cat.jpg",
        cacheKey = "s3:true:s3.amazonaws.com:us-east-1:photos:album/cat.jpg:cat-v1",
        headers = mapOf("X-Playback-Token" to "token-value"),
        refreshSignedRequest = { initialSigned },
    )

    @Test
    fun directResolvedModelBuildsRequestFromResolvedValues() {
        assertResolvedRequest(buildImageRequest(PlatformContext.INSTANCE, model))
    }

    @Test
    fun dataWithTypeUnwrapsResolvedModelBeforeBuildingRequest() {
        val wrapped = DataWithType(model, "image/jpeg")

        assertResolvedRequest(buildImageRequest(PlatformContext.INSTANCE, wrapped))
    }

    @Test
    fun outerRequestNeverContainsSensitiveResolvedHeaders() {
        val sourceHeaders = linkedMapOf("Authorization" to "Bearer original-secret")
        val signed = SignedS3ObjectUrl(
            "https://signed.example/album/cat.jpg?signature=rotating",
            Long.MAX_VALUE,
        )
        val resolved = ResolvedImageModel(
            initialSignedRequest = signed,
            stableKey = "album/cat.jpg",
            cacheKey = "s3:stable-cache-key",
            headers = sourceHeaders,
            refreshSignedRequest = { signed },
        )
        sourceHeaders["Authorization"] = "Bearer mutated-secret"

        val request = buildImageRequest(PlatformContext.INSTANCE, resolved)

        assertSame(resolved, request.data)
        assertTrue(request.httpHeaders.asMap().isEmpty())
        assertFalse(request.extras.toString().contains("original-secret"))
        assertFalse(request.toString().contains("original-secret"))
        assertFalse(request.toString().contains("mutated-secret"))
    }

    private fun assertResolvedRequest(request: coil3.request.ImageRequest) {
        assertSame(model, request.data)
        assertEquals(model.cacheKey, request.memoryCacheKey)
        assertEquals(model.cacheKey, request.diskCacheKey)
        assertTrue(request.httpHeaders.asMap().isEmpty())

        assertNotEquals(model.stableKey, request.data)
        assertNotEquals<Any?>(model.stableKey, request.memoryCacheKey)
        assertNotEquals<Any?>(model.stableKey, request.diskCacheKey)
        assertFalse(request.toString().contains("signature=rotating"))
        assertFalse(request.toString().contains("token-value"))
        assertFalse(request.extras.toString().contains("token-value"))
    }
}
