package com.alpha.ai.imagegeneration

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val OPERATION_ID = OperationId("operation-1")
val PNG = "image/png"
val IMAGE = GeneratedImage(
    bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
    mimeType = PNG,
    providerId = ProviderId("fake"),
    model = "fake-model",
)

open class FakeConfig(
    override val apiToken: SecretValue = SecretValue("token"),
    override val protocolVersion: String = "v1",
    override val allowInsecureHttp: Boolean = false,
) : ProviderRuntimeConfig {
    override open val providerId = ProviderId("fake")
    override val baseUrl = "https://fake.test/"
    override val model = "fake-model"
}

class OtherConfig : FakeConfig() {
    override val providerId = ProviderId("other")
}

data object OtherOptions : ProviderRequestOptions {
    override val providerId = ProviderId("other")
}

fun request(
    operationId: OperationId = OPERATION_ID,
    source: InputStreamImageSource = fixtureSource(),
    prompt: String = "a test image",
    options: ProviderRequestOptions? = null,
) = GenerateImageRequest(operationId, source, prompt, options)

fun fixtureSource(bytes: ByteArray = byteArrayOf(9, 8, 7)): InputStreamImageSource = object : InputStreamImageSource {
    override val mimeType = PNG
    override val fileName = "source.png"
    override val contentLength = bytes.size.toLong()
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)
}

class TrackingSource(private val bytes: ByteArray = byteArrayOf(9, 8, 7)) : InputStreamImageSource {
    var opened = 0
        private set
    var closed = 0
        private set

    override val mimeType = PNG
    override val fileName = "source.png"
    override val contentLength = bytes.size.toLong()

    override fun openStream(): InputStream {
        opened++
        return object : FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun close() {
                super.close()
                closed++
            }
        }
    }
}

class FakeProvider : ImageGenerationProvider<FakeConfig> {
    var calls: Int = 0
    override val descriptor = ProviderDescriptor(
        id = ProviderId("fake"),
        displayName = "Fake",
        supportedProtocolVersions = setOf("v1"),
        capabilities = ProviderCapabilities.conservative(setOf(PNG), 1024),
    )
    override val configType: KClass<FakeConfig> = FakeConfig::class

    override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> = flow {
        calls++
        emit(ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)))
    }
}

fun List<ImageGenerationEvent>.lastCompleted(): ImageGenerationEvent.Completed =
    filterIsInstance<ImageGenerationEvent.Completed>().last()

fun List<ProviderGenerationEvent>.lastProviderCompleted(): ProviderGenerationEvent.Completed =
    filterIsInstance<ProviderGenerationEvent.Completed>().last()

fun GenerationResult.failureOrNull(): GenerationResult.Failure? = this as? GenerationResult.Failure

fun successImage(bytes: ByteArray = IMAGE.bytes.copyOf()): GeneratedImage = IMAGE.copy(bytes = bytes)

fun jsonResponse(body: String): InputStream = body.byteInputStream(StandardCharsets.UTF_8)

fun ByteArray.base64(): String = java.util.Base64.getEncoder().encodeToString(this)
