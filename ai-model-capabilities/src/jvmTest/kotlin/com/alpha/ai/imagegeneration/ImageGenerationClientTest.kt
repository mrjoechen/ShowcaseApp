package com.alpha.ai.imagegeneration

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ImageGenerationClientTest {
    @Test
    fun `provider descriptor is available by open provider id`() {
        val provider = FakeProvider()
        val client = ImageGeneration.builder().register(provider).build()

        assertEquals(provider.descriptor, client.providerDescriptor(ProviderId("fake")))
        assertNull(client.providerDescriptor(ProviderId("missing")))
    }

    @Test
    fun `repeatable source streams are independently opened and closed`() = runTest {
        val source = TrackingSource()
        val provider = object : ImageGenerationProvider<FakeConfig> {
            override val descriptor = FakeProvider().descriptor.copy(
                capabilities = ProviderCapabilities.conservative(setOf(PNG)),
            )
            override val configType = FakeConfig::class
            override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> = flow {
                request.source.openSource().buffer().use { it.readByteArray() }
                request.source.openSource().buffer().use { it.readByteArray() }
                emit(ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)))
            }
        }

        ImageGeneration.builder().register(provider).build().generate(request(source = source), FakeConfig()).toList()

        assertEquals(2, source.opened)
        assertEquals(2, source.closed)
    }

    @Test
    fun `registered provider receives its typed config`() = runTest {
        val provider = FakeProvider()
        val client = ImageGeneration.builder().register(provider).build()

        val events = client.generate(request(), FakeConfig()).toList()

        assertEquals(ImageGenerationEvent.Stage(OPERATION_ID, GenerationStage.VALIDATING_CONFIGURATION), events[0])
        val success = (events[1] as ImageGenerationEvent.Completed).result as GenerationResult.Success
        assertEquals(IMAGE.mimeType, success.image.mimeType)
        assertContentEquals(IMAGE.bytes, success.image.bytes)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `duplicate provider id fails at build time`() {
        assertFailsWith<IllegalArgumentException> {
            ImageGeneration.builder().register(FakeProvider()).register(FakeProvider()).build()
        }
    }

    @Test
    fun `options for another provider fail before dispatch`() = runTest {
        val provider = FakeProvider()
        val terminal = ImageGeneration.builder().register(provider).build()
            .generate(request(options = OtherOptions), FakeConfig())
            .last() as ImageGenerationEvent.Completed
        assertEquals(ImageGenerationErrorCategory.INVALID_REQUEST, terminal.result.failureOrNull()?.error?.category)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `unknown provider emits typed configuration failure`() = runTest {
        val terminal = ImageGeneration.builder().build()
            .generate(request(), FakeConfig())
            .last() as ImageGenerationEvent.Completed
        assertEquals(ImageGenerationErrorCategory.CONFIGURATION, terminal.result.failureOrNull()?.error?.category)
    }

    @Test
    fun `source open failure is normalized before provider dispatch`() = runTest {
        assertSourceFailureIsNormalized(object : InputStreamImageSource {
            override val mimeType = PNG
            override val fileName = "source.png"
            override val contentLength: Long? = null
            override fun openStream(): InputStream = throw IOException("sensitive open failure")
        })
    }

    @Test
    fun `source read failure is normalized before provider dispatch`() = runTest {
        assertSourceFailureIsNormalized(object : InputStreamImageSource {
            override val mimeType = PNG
            override val fileName = "source.png"
            override val contentLength: Long? = null
            override fun openStream(): InputStream = object : InputStream() {
                override fun read(): Int = throw IOException("sensitive read failure")
            }
        })
    }

    @Test
    fun `source close failure is normalized before provider dispatch`() = runTest {
        assertSourceFailureIsNormalized(object : InputStreamImageSource {
            override val mimeType = PNG
            override val fileName = "source.png"
            override val contentLength: Long? = null
            override fun openStream(): InputStream = object : ByteArrayInputStream(byteArrayOf(1)) {
                override fun close() = throw IOException("sensitive close failure")
            }
        })
    }

    @Test
    fun `source preflight preserves caller cancellation`() = runTest {
        val cancellation = CancellationException("caller cancelled source")
        val source = object : InputStreamImageSource {
            override val mimeType = PNG
            override val fileName = "source.png"
            override val contentLength: Long? = null
            override fun openStream(): InputStream = throw cancellation
        }

        val thrown = assertFailsWith<CancellationException> {
            ImageGeneration.builder().register(FakeProvider()).build().generate(request(source = source), FakeConfig()).toList()
        }

        assertEquals(cancellation, thrown)
    }

    private suspend fun assertSourceFailureIsNormalized(source: InputStreamImageSource) {
        val provider = FakeProvider()

        val terminal = ImageGeneration.builder().register(provider).build()
            .generate(request(source = source), FakeConfig()).last() as ImageGenerationEvent.Completed
        val failure = terminal.result.failureOrNull()?.error

        assertEquals(ImageGenerationErrorCategory.INVALID_REQUEST, failure?.category)
        assertEquals(RetryAdvice.DO_NOT_RETRY, failure?.retryAdvice)
        assertEquals(false, failure?.requestMayHaveBeenAccepted)
        assertEquals("Unable to read input source", failure?.safeMessage)
        assertEquals(0, provider.calls)
    }
}
