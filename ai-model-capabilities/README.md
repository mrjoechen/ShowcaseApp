# AI model capabilities

`ai-model-capabilities` is a Kotlin Multiplatform library for Android/JVM and iOS that normalizes image model capabilities behind one provider-neutral client. It currently supports image-to-image generation and schema-constrained image understanding. The public seam is `AiModelClient`; concrete models are `AiProviderAdapter` implementations, so adding a provider does not require changing upper-layer calls.

Runtime configuration belongs to the caller: create it in memory after loading a credential, use it for a request, and close the `SecretValue` when the caller's credential lifetime ends. Do not put tokens in provider descriptors, persisted config, logs, request IDs, metadata, errors, or URLs.

`GenerateImageRequest.source` must be repeatable. The client and a provider may open it more than once; every `openSource()` call must return a fresh Okio `Source` and each consumer closes its source. Collection is cancellable: cancelling the returned `Flow` cancels the active HTTP call on a best-effort basis and does not turn cancellation into a success or retry.

## Built-ins

Register all built-ins with one shared internal transport:

```kotlin
import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns

val client = AiModel.builder().registerBuiltIns().build()
```

The convenience IDs are not a closed provider enum. Custom providers remain supported.

## Structured request logs

Logging is disabled by default. Install an `AiRequestLogger` when the host application needs
request lifecycle and timing diagnostics:

```kotlin
val client = AiModel.builder()
    .requestLogger { event -> applicationLogger.log(event) }
    .registerBuiltIns()
    .build()
```

Both image generation and image understanding emit `Started`, `Stage`, and `Completed` events.
Stage and terminal events include monotonic `elapsedMillis`; terminal events also include the
normalized outcome and error category. Events expose only provider/model identifiers, input MIME
and byte count, and a one-way operation fingerprint. API tokens, base URLs, prompts, file names,
provider request IDs, and response payloads are deliberately unavailable to the logger. Logger
exceptions never change request behavior.

| ID | Protocol | Input ceiling |
| --- | --- | --- |
| `openai` | `openai-images-v1` | PNG, JPEG, or WebP; 12 MiB |
| `gemini-nano-banana` | `gemini-generate-content-v1beta` | PNG, JPEG, or WebP; 12 MiB |
| `openai-vision` | `openai-chat-completions-v1` | OpenAI-compatible multimodal JSON; 12 MiB |
| `gemini-vision` | `gemini-generate-content-v1beta` | Gemini multimodal JSON; 12 MiB |
| `deepseek-vision` | `openai-chat-completions-v1` | DeepSeek multimodal input (OpenAI-compatible); 32 MiB |

## OpenAI and Gemini

```kotlin
import com.alpha.ai.imagegeneration.GenerateImageRequest
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.provider.gemini.GeminiImageConfig
import com.alpha.ai.imagegeneration.provider.openai.OpenAiImageConfig
import kotlinx.coroutines.flow.collect

val openAiToken = SecretValue(loadOpenAiToken())
val geminiToken = SecretValue(loadGeminiToken())
try {
    val openAi = OpenAiImageConfig(openAiToken, model = "gpt-image-1")
    val gemini = GeminiImageConfig(geminiToken, model = "gemini-2.5-flash-image")

    client.generate(
        GenerateImageRequest(OperationId("edit-42"), source, prompt = "A warm watercolor portrait"),
        openAi,
    ).collect { event -> render(event) }

    client.generate(
        GenerateImageRequest(OperationId("edit-43"), source, prompt = "A warm watercolor portrait"),
        gemini,
    ).collect { event -> render(event) }
} finally {
    openAiToken.close()
    geminiToken.close()
}
```

The client validates and normalizes terminal results. It redacts active runtime tokens from returned strings, drops unapproved metadata keys, rejects unsafe retained URLs (including query, fragment, or userinfo), and bounds image and response bodies.

## Model discovery

Call `AiModelClient.listModels(ProviderModelCatalogRequest(...))` before a model is selected. The request deliberately contains no model name. OpenAI-compatible, DeepSeek, and Gemini adapters return normalized `ProviderModel` entries; adapters without a model-catalog API return `ProviderModelCatalogResult.Unsupported`. A host UI should keep the model field editable even after the user chooses a returned item.

Model discovery is an optional `AiProviderAdapter.listModels` method. A new provider adds its own implementation without adding provider switches to application code.

## Image understanding

The understanding API accepts the same repeatable `ImageSource` used by image generation and returns a `JsonObject`. The schema is provider-neutral: Gemini sends it as `responseSchema`, while OpenAI-compatible and DeepSeek adapters send JSON mode plus the same schema as an explicit prompt constraint.

```kotlin
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingEvent
import com.alpha.ai.imagegeneration.ImageUnderstandingResult
import com.alpha.ai.imagegeneration.ImageUnderstandingSchema
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.provider.deepseek.DeepSeekVisionConfig

val token = SecretValue(loadDeepSeekToken())
try {
    val request = ImageUnderstandingRequest(
        operationId = OperationId("understand-42"),
        source = source,
        prompt = "识别主体、场景和适合小红书的图片文案，输出简洁准确的字段。",
        responseSchema = ImageUnderstandingSchema.default(),
    )
    client.understandImage(
        request,
        DeepSeekVisionConfig(token, model = "deepseek-chat"),
    ).collect { event ->
        if (event is ImageUnderstandingEvent.Completed && event.result is ImageUnderstandingResult.Success) {
            val json = event.result.output.data
            renderUnderstanding(json)
        }
    }
} finally {
    token.close()
}
```

`OpenAiVisionConfig` accepts a custom `baseUrl`, so any compatible `/chat/completions` endpoint can be configured without changing the caller. DeepSeek uses the same wire adapter with a separate provider ID. Add another model by implementing `ImageUnderstandingProvider` (or `AiProviderAdapter` directly), registering it with the builder, and leaving the `AiModelClient` call unchanged.

## Custom providers

Extensions are trusted code. They own their HTTP implementation and networking policy; client registration cannot intercept arbitrary custom networking. A custom provider must itself implement same-origin redirect handling, bounded response bodies, credential forwarding only where intended, cancellation of active work, and no transparent retries unless the provider contract proves a retry is safe. The client still enforces its normalized event/result/redaction contract after registration.

```kotlin
import com.alpha.ai.imagegeneration.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeConfig(
    override val apiToken: SecretValue,
    override val baseUrl: String = "https://fake.example",
    override val allowInsecureHttp: Boolean = false,
    override val protocolVersion: String = "fake-v1",
) : ProviderRuntimeConfig {
    override val providerId = ProviderId("fake")
    override val model: String? = null
}

data class FakeOptions(val variant: String) : ProviderRequestOptions {
    init {
        require(variant.isNotBlank())
    }

    override val providerId = ProviderId("fake")
}

class FakeProvider : ImageGenerationProvider<FakeConfig> {
    override val configType = FakeConfig::class
    override val descriptor = ProviderDescriptor(
        id = ProviderId("fake"),
        displayName = "Fake Provider",
        supportedProtocolVersions = setOf("fake-v1"),
        capabilities = ProviderCapabilities.conservative(
            supportedInputMimeTypes = setOf("image/jpeg"),
            maxInputBytes = 1L * 1024 * 1024,
        ),
        safeMetadataKeys = setOf("variant"),
    )

    override fun generate(
        request: GenerateImageRequest,
        config: FakeConfig,
    ): Flow<ProviderGenerationEvent> = flow {
        val options = request.providerOptions as? FakeOptions
        if (options == null) {
            emit(ProviderGenerationEvent.Completed(GenerationResult.Failure(
                ImageGenerationError(
                    category = ImageGenerationErrorCategory.INVALID_REQUEST,
                    retryAdvice = RetryAdvice.DO_NOT_RETRY,
                    requestMayHaveBeenAccepted = false,
                ),
            )))
            return@flow
        }

        // A real extension performs its own bounded, cancellable, no-transparent-retry request here.
        val resultBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00)
        check(resultBytes.size <= MAX_RESULT_BYTES)
        emit(ProviderGenerationEvent.Stage(GenerationStage.UPLOADING))
        emit(ProviderGenerationEvent.Completed(GenerationResult.Success(
            GeneratedImage(
                bytes = resultBytes,
                mimeType = "image/jpeg",
                providerId = config.providerId,
                model = config.model,
                metadata = mapOf("variant" to options.variant),
            ),
        )))
    }

    private companion object {
        const val MAX_RESULT_BYTES = 1_024
    }
}

val customClient = ImageGeneration.builder()
    .registerBuiltIns()
    .register(FakeProvider())
    .build()
```

The fake's `variant` is the only metadata key the client permits from that provider. Use conservative capabilities unless the extension can prove its input limits, idempotency behavior, retry phases, and cancellation semantics.

## Multiplatform integration

The module's contracts, client normalization, validation, and all built-in providers live in
`commonMain`. JVM/Android use the existing OkHttp transport; iOS Arm64 and simulator Arm64 use
Ktor Darwin. Both transports bound response bodies, reject cross-origin redirects, avoid automatic
POST retries, and propagate caller cancellation. JS and Wasm artifacts expose the shared API but
fail before dispatching provider requests. Hosts must also hide AI entry points on browser targets.

```kotlin
val source = ByteArrayImageSource(imageBytes, mimeType = "image/png", fileName = "photo.png")
val client = AiModel.builder().registerBuiltIns().build()
```

`ByteArrayImageSource` owns a snapshot of the input array and supplies independent sources.
Existing Android/JVM stream implementations can implement `InputStreamImageSource` and retain
`openStream(): InputStream`; its default `openSource()` bridges to Okio.

This repository maintains its own KMP module at `ai-model-capabilities/`. It is included as
`:ai-model-capabilities` by the root settings and consumed by `composeApp`; no sibling repository
or `aiModelCapabilitiesDir` property is required. Plugins and dependencies use this project's
`gradle/libs.versions.toml`. Generated files use the normal `ai-model-capabilities/build` directory.

Android, iOS and desktop clients expose provider settings and AI creations. During playback,
interact with an image to show the generation action. Image summaries can be enabled in slide,
fade and calendar mode settings. Browser targets hide these controls and disable AI execution.

Run `:ai-model-capabilities:jvmTest` for the existing provider/client regressions and portable
transport tests. Compile `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`,
`compileKotlinJs`, and `compileKotlinWasmJs` to check platform artifacts.
