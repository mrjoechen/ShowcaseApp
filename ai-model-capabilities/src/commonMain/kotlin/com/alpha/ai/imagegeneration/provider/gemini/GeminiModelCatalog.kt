package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ProviderModel
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.hasJsonContentType
import com.alpha.ai.imagegeneration.internal.http.toProviderUrlBuilder
import com.alpha.ai.imagegeneration.provider.openai.httpCategory
import com.alpha.ai.imagegeneration.provider.openai.modelCatalogUnavailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MODEL_CATALOG_RESPONSE_LIMIT = 1024L * 1024L

internal suspend fun listGeminiModels(
    transport: HttpTransport,
    request: ProviderModelCatalogRequest,
): ProviderModelCatalogResult {
    val httpRequest = try {
        val endpoint = BaseUrlValidator.validate(request.baseUrl, request.allowInsecureHttp)
            .toProviderUrlBuilder()
            .addPathSegment("models")
            .addQueryParameter("pageSize", "1000")
            .build()
        request.apiToken.use { token ->
            HttpRequest.get(endpoint, mapOf("x-goog-api-key" to token.concatToString()))
        }
    } catch (_: ImageGenerationTransportException) {
        return modelCatalogUnavailable(ImageGenerationErrorCategory.CONFIGURATION)
    }
    val running = try {
        transport.prepare(httpRequest, MODEL_CATALOG_RESPONSE_LIMIT).start()
    } catch (_: ImageGenerationTransportException) {
        return modelCatalogUnavailable(ImageGenerationErrorCategory.NETWORK)
    }
    val response = try {
        running.await()
    } catch (error: CancellationException) {
        throw error
    } catch (_: ImageGenerationTransportException) {
        return modelCatalogUnavailable(ImageGenerationErrorCategory.NETWORK)
    } finally {
        running.cancelIfActive()
    }
    if (response.status !in 200..299) return modelCatalogUnavailable(httpCategory(response.status), response.status)
    if (!response.hasJsonContentType()) return modelCatalogUnavailable(ImageGenerationErrorCategory.MALFORMED_RESPONSE)
    val models = try {
        JSON.decodeFromString<GeminiModelListResponse>(response.body.decodeToString()).models
            .asSequence()
            .filter { "generateContent" in it.supportedGenerationMethods }
            .map { model ->
                ProviderModel(
                    id = model.baseModelId.ifBlank { model.name.removePrefix("models/") },
                    displayName = model.displayName.takeIf(String::isNotBlank),
                )
            }
            .toList()
    } catch (_: Exception) {
        return modelCatalogUnavailable(ImageGenerationErrorCategory.MALFORMED_RESPONSE)
    }
    return ProviderModelCatalogResult.Available(request.providerId, request.capability, models)
}

@Serializable
private data class GeminiModelListResponse(val models: List<GeminiModelItem>)

@Serializable
private data class GeminiModelItem(
    val name: String = "",
    val baseModelId: String = "",
    val displayName: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
)

private val JSON = Json { ignoreUnknownKeys = true }
