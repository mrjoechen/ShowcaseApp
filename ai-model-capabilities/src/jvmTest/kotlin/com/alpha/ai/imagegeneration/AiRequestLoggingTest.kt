package com.alpha.ai.imagegeneration

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AiRequestLoggingTest {
    @Test
    fun `generation emits structured lifecycle events with elapsed time`() = runTest {
        val logs = mutableListOf<AiRequestLogEvent>()
        val client = ImageGeneration.builder()
            .requestLogger { logs += it }
            .register(FakeProvider())
            .build()

        client.generate(request(), FakeConfig()).toList()

        val started = logs.first() as AiRequestLogEvent.Started
        val completed = logs.last() as AiRequestLogEvent.Completed
        assertEquals(AiCapability.IMAGE_TO_IMAGE, started.capability)
        assertEquals("fake", started.providerId.value)
        assertEquals("fake-model", started.model)
        assertEquals(PNG, started.inputMimeType)
        assertEquals(3L, started.inputBytes)
        assertTrue(started.operationFingerprint.matches(Regex("[0-9a-f]{12}")))
        assertFalse(started.operationFingerprint.contains(OPERATION_ID.value))
        assertTrue(logs.filterIsInstance<AiRequestLogEvent.Stage>().isNotEmpty())
        assertEquals(AiRequestOutcome.SUCCESS, completed.outcome)
        assertNull(completed.errorCategory)
        assertTrue(completed.elapsedMillis >= 0L)
    }

    @Test
    fun `understanding and validation failures use the same logging contract`() = runTest {
        val logs = mutableListOf<AiRequestLogEvent>()
        val client = AiModel.builder()
            .requestLogger { logs += it }
            .register(FakeUnderstandingProvider())
            .build()

        client.understandImage(
            ImageUnderstandingRequest(OperationId("vision-operation"), fixtureSource(), "Summarize"),
            FakeConfig(),
        ).toList()

        assertEquals(AiCapability.IMAGE_UNDERSTANDING, logs.first().capability)
        assertEquals(AiRequestOutcome.SUCCESS, (logs.last() as AiRequestLogEvent.Completed).outcome)

        logs.clear()
        ImageGeneration.builder()
            .requestLogger { logs += it }
            .build()
            .generate(request(), FakeConfig())
            .toList()

        val failure = logs.last() as AiRequestLogEvent.Completed
        assertEquals(AiRequestOutcome.FAILURE, failure.outcome)
        assertEquals(ImageGenerationErrorCategory.CONFIGURATION, failure.errorCategory)
    }

    @Test
    fun `credential-shaped model and logger failures cannot leak or alter request results`() = runTest {
        val logs = mutableListOf<AiRequestLogEvent>()
        val secretModelConfig = object : ProviderRuntimeConfig {
            override val providerId = ProviderId("missing")
            override val baseUrl = "https://example.test"
            override val model = "secret-token"
            override val protocolVersion = "v1"
            override val apiToken = SecretValue("secret-token")
            override val allowInsecureHttp = false
        }
        val terminal = ImageGeneration.builder()
            .requestLogger { event -> logs += event }
            .build()
            .generate(request(), secretModelConfig)
            .toList()

        assertNull((logs.first() as AiRequestLogEvent.Started).model)
        assertTrue(terminal.last() is ImageGenerationEvent.Completed)

        val stillCompletes = ImageGeneration.builder()
            .requestLogger { throw IllegalStateException("logging backend unavailable") }
            .register(FakeProvider())
            .build()
            .generate(request(), FakeConfig())
            .toList()
        assertTrue(stillCompletes.last() is ImageGenerationEvent.Completed)
    }

    private class FakeUnderstandingProvider : ImageUnderstandingProvider<FakeConfig> {
        override val descriptor = ProviderDescriptor(
            id = ProviderId("fake"),
            displayName = "Fake Vision",
            supportedProtocolVersions = setOf("v1"),
            capabilities = ProviderCapabilities.conservative(setOf(PNG), 1024).copy(
                supportedCapabilities = setOf(AiCapability.IMAGE_UNDERSTANDING),
            ),
        )
        override val configType: KClass<FakeConfig> = FakeConfig::class

        override fun understand(
            request: ImageUnderstandingRequest,
            config: FakeConfig,
        ): Flow<ProviderUnderstandingEvent> = flow {
            emit(ProviderUnderstandingEvent.Stage(GenerationStage.PREPARING_INPUT))
            emit(ProviderUnderstandingEvent.Stage(GenerationStage.UPLOADING))
            emit(ProviderUnderstandingEvent.Stage(GenerationStage.GENERATING))
            emit(
                ProviderUnderstandingEvent.Completed(
                    ImageUnderstandingResult.Success(
                        ImageUnderstandingOutput(
                            data = buildJsonObject { put("summary", JsonPrimitive("ok")) },
                            providerId = config.providerId,
                            model = config.model,
                        ),
                    ),
                ),
            )
        }
    }
}
