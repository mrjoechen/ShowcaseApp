package com.alpha.ai.imagegeneration.internal.http

import com.alpha.ai.imagegeneration.GenerateImageRequest
import com.alpha.ai.imagegeneration.GeneratedImage
import com.alpha.ai.imagegeneration.GenerationResult
import com.alpha.ai.imagegeneration.ImageGeneration
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ImageGenerationProvider
import com.alpha.ai.imagegeneration.ProviderGenerationEvent
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.fixtureSource
import com.alpha.ai.imagegeneration.lastCompleted
import com.alpha.ai.imagegeneration.request
import com.alpha.ai.imagegeneration.failureOrNull
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ImageBytesValidatorTest {
    @Test
    fun `recognizes PNG JPEG and WebP base64 images`() {
        assertEquals("image/png", ImageBytesValidator.decodeBase64(png().base64(), null, 64).mimeType)
        assertEquals("image/jpeg", ImageBytesValidator.decodeBase64(jpeg().base64(), null, 64).mimeType)
        assertEquals("image/webp", ImageBytesValidator.decodeBase64(webp().base64(), null, 64).mimeType)
    }

    @Test
    fun `rejects image bytes whose declared MIME does not match signature`() {
        assertFailsWith<ImageGenerationTransportException> {
            ImageBytesValidator.validate(png(), declaredMimeType = "image/jpeg", limitBytes = 64)
        }
    }

    @Test
    fun `rejects base64 whose decoded size exceeds the limit before decoding`() {
        assertFailsWith<ImageGenerationTransportException> {
            ImageBytesValidator.decodeBase64("QUJDREVGR0g=", declaredMimeType = null, limitBytes = 7)
        }
    }

    @Test
    fun `custom provider invalid success becomes malformed response`() = runTest {
        val provider = provider(GeneratedImage(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/png",
            providerId = ProviderId("custom"),
            model = null,
        ))

        val terminal = ImageGeneration.builder().register(provider).build()
            .generate(request(source = fixtureSource()), Config())
            .toList()
            .lastCompleted()

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, terminal.result.failureOrNull()?.error?.category)
    }

    @Test
    fun `custom provider image is copied before leaving module`() = runTest {
        val providerBytes = png()
        val provider = provider(GeneratedImage(
            bytes = providerBytes,
            mimeType = "image/png",
            providerId = ProviderId("custom"),
            model = null,
        ))

        val success = ImageGeneration.builder().register(provider).build()
            .generate(request(source = fixtureSource()), Config())
            .toList()
            .lastCompleted()
            .result as GenerationResult.Success
        providerBytes[0] = 0

        assertContentEquals(png(), success.image.bytes)
    }

    private fun provider(image: GeneratedImage): ImageGenerationProvider<Config> = object : ImageGenerationProvider<Config> {
        override val descriptor = com.alpha.ai.imagegeneration.ProviderDescriptor(
            id = ProviderId("custom"),
            displayName = "Custom",
            supportedProtocolVersions = setOf("v1"),
            capabilities = com.alpha.ai.imagegeneration.ProviderCapabilities.conservative(),
        )
        override val configType: KClass<Config> = Config::class
        override fun generate(request: GenerateImageRequest, config: Config): Flow<ProviderGenerationEvent> =
            flowOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image)))
    }

    private class Config : ProviderRuntimeConfig {
        override val providerId = ProviderId("custom")
        override val baseUrl = "https://api.example.test/v1/"
        override val model: String? = null
        override val protocolVersion = "v1"
        override val apiToken = com.alpha.ai.imagegeneration.SecretValue("token")
        override val allowInsecureHttp = false
    }

    private fun ByteArray.base64(): String = java.util.Base64.getEncoder().encodeToString(this)
    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private fun jpeg() = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00)
    private fun webp() = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
}
