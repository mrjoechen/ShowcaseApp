package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ResolvedImageModelRefreshTest {

    @Test
    fun freshSignedRequestDoesNotRefresh() = runTest {
        val now = 1_000L
        val initial = signed("https://signed.example/fresh?credential=secret", now + 600_000L)
        var refreshCount = 0
        val model = model(initial) {
            refreshCount += 1
            signed("https://signed.example/unexpected", now + 1_000_000L)
        }

        assertSame(initial, model.resolveForFetch(now))
        assertEquals(0, refreshCount)
    }

    @Test
    fun requestInsideSafetyWindowRefreshesWithoutChangingStableIdentity() = runTest {
        val now = 2_000L
        val initial = signed(
            "https://signed.example/old?credential=old-secret",
            now + S3_SIGNED_URL_REFRESH_WINDOW_MILLIS,
        )
        val refreshed = signed("https://signed.example/new?credential=new-secret", now + 600_000L)
        var refreshCount = 0
        val model = model(initial) {
            refreshCount += 1
            refreshed
        }
        val rotatedModel = model(refreshed) { refreshed }

        assertSame(refreshed, model.resolveForFetch(now))
        assertEquals(1, refreshCount)
        assertEquals("album/cat.jpg", model.stableKey)
        assertEquals("s3:stable", model.cacheKey)
        assertEquals(model, rotatedModel)
        assertEquals(model.hashCode(), rotatedModel.hashCode())
    }

    @Test
    fun refreshFailurePropagatesWithoutReplacingCurrentRequest() = runTest {
        val now = 3_000L
        val initial = signed("https://signed.example/old?credential=secret", now)
        val failure = IllegalStateException("raw signed url must not reach logs")
        val model = model(initial) { throw failure }

        val thrown = assertFailsWith<IllegalStateException> { model.resolveForFetch(now) }

        assertSame(failure, thrown)
    }

    @Test
    fun refreshCancellationPropagatesWithIdentityPreserved() = runTest {
        val now = 4_000L
        val cancellation = CancellationException("fetch cancelled")
        val model = model(signed("https://signed.example/old?credential=secret", now)) {
            throw cancellation
        }

        val thrown = assertFailsWith<CancellationException> { model.resolveForFetch(now) }

        assertSame(cancellation, thrown)
    }

    @Test
    fun caseInsensitiveDuplicateHeadersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            model(
                signed("https://signed.example/cat?credential=secret", Long.MAX_VALUE),
                headers = linkedMapOf(
                    "Authorization" to "Bearer first",
                    "authorization" to "Bearer second",
                ),
            ) { error("unused") }
        }
    }

    @Test
    fun signedRequestAndModelStringRepresentationsAreSecretSafe() {
        val rawUrl = "https://signed.example/cat?credential=url-secret"
        val signed = signed(rawUrl, Long.MAX_VALUE)
        val model = model(signed, headers = mapOf("Authorization" to "Bearer header-secret")) {
            signed
        }

        listOf(signed.toString(), model.toString(), DataWithType(model, "image/jpeg").toString())
            .forEach { rendered ->
                kotlin.test.assertFalse(rendered.contains(rawUrl))
                kotlin.test.assertFalse(rendered.contains("url-secret"))
                kotlin.test.assertFalse(rendered.contains("header-secret"))
            }
    }

    private fun model(
        initial: SignedS3ObjectUrl,
        headers: Map<String, String> = emptyMap(),
        refresh: suspend () -> SignedS3ObjectUrl,
    ) = ResolvedImageModel(
        initialSignedRequest = initial,
        stableKey = "album/cat.jpg",
        cacheKey = "s3:stable",
        headers = headers,
        refreshSignedRequest = refresh,
    )

    private fun signed(url: String, expiresAtEpochMillis: Long) =
        SignedS3ObjectUrl(url, expiresAtEpochMillis)
}
