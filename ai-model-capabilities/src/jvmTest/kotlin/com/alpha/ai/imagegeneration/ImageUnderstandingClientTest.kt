package com.alpha.ai.imagegeneration

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class ImageUnderstandingClientTest {
    @Test
    fun `new understanding capability crosses the adapter seam without changing client calls`() = runTest {
        val provider = object : ImageUnderstandingProvider<FakeConfig> {
            override val descriptor = ProviderDescriptor(
                id = ProviderId("fake"),
                displayName = "Fake Vision",
                supportedProtocolVersions = setOf("v1"),
                capabilities = ProviderCapabilities.conservative(setOf(PNG), 1024).copy(
                    supportedCapabilities = setOf(AiCapability.IMAGE_UNDERSTANDING),
                ),
            )
            override val configType: KClass<FakeConfig> = FakeConfig::class
            override fun understand(request: ImageUnderstandingRequest, config: FakeConfig): Flow<ProviderUnderstandingEvent> = flow {
                emit(ProviderUnderstandingEvent.Stage(GenerationStage.PREPARING_INPUT))
                emit(ProviderUnderstandingEvent.Stage(GenerationStage.UPLOADING))
                emit(ProviderUnderstandingEvent.Stage(GenerationStage.GENERATING))
                emit(ProviderUnderstandingEvent.Completed(ImageUnderstandingResult.Success(
                    ImageUnderstandingOutput(
                        data = kotlinx.serialization.json.buildJsonObject { put("summary", JsonPrimitive("ok")) },
                        providerId = config.providerId,
                        model = config.model,
                    ),
                )))
            }
        }
        val client: AiModelClient = AiModel.builder().register(provider).build()

        val events = client.understandImage(
            ImageUnderstandingRequest(OperationId("understand-1"), fixtureSource(), "Summarize"),
            FakeConfig(),
        ).toList()

        val result = (events.last() as ImageUnderstandingEvent.Completed).result as ImageUnderstandingResult.Success
        assertEquals("ok", result.output.data.getValue("summary").toString().trim('"'))
        assertTrue(events.any { it is ImageUnderstandingEvent.Stage && it.value == GenerationStage.GENERATING })
    }
}
