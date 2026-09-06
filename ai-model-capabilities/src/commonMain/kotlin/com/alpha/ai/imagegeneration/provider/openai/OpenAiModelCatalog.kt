package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ProviderModel
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.hasJsonContentType
import com.alpha.ai.imagegeneration.internal.http.toProviderUrlBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MODEL_CATALOG_RESPONSE_LIMIT = 512L * 1024L

internal suspend fun listOpenAiModels(
    transport: HttpTransport,
    request: ProviderModelCatalogRequest,
): ProviderModelCatalogResult {
    val httpRequest = try {
        val endpoint = BaseUrlValidator.validate(request.baseUrl, request.allowInsecureHttp)
            .toProviderUrlBuilder()
            .addPathSegment("models")
            .build()
        request.apiToken.use { token ->
            HttpRequest.get(endpoint, mapOf("Authorization" to "Bearer ${token.concatToString()}"))
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
        JSON.decodeFromString<OpenAiModelListResponse>(response.body.decodeToString()).data
            .map { ProviderModel(id = it.id) }
    } catch (_: Exception) {
        return modelCatalogUnavailable(ImageGenerationErrorCategory.MALFORMED_RESPONSE)
    }
    return ProviderModelCatalogResult.Available(request.providerId, request.capability, models)
}

@Serializable
private data class OpenAiModelListResponse(val data: List<OpenAiModelItem>)

@Serializable
private data class OpenAiModelItem(val id: String)

private val JSON = Json { ignoreUnknownKeys = true }

internal fun modelCatalogUnavailable(
    category: ImageGenerationErrorCategory,
    status: Int? = null,
) = ProviderModelCatalogResult.Unavailable(
    ImageGenerationError(
        category = category,
        retryAdvice = if (category in RETRYABLE_CATALOG_ERRORS) RetryAdvice.SAFE_TO_RETRY else RetryAdvice.DO_NOT_RETRY,
        requestMayHaveBeenAccepted = false,
        httpStatus = status,
        definitelyUnprocessed = true,
    ),
)

internal fun httpCategory(status: Int): ImageGenerationErrorCategory = when (status) {
    401, 403 -> ImageGenerationErrorCategory.AUTHENTICATION
    408 -> ImageGenerationErrorCategory.TIMEOUT
    429 -> ImageGenerationErrorCategory.RATE_LIMITED
    in 500..599 -> ImageGenerationErrorCategory.SERVER
    else -> ImageGenerationErrorCategory.CONFIGURATION
}

private val RETRYABLE_CATALOG_ERRORS = setOf(
    ImageGenerationErrorCategory.NETWORK,
    ImageGenerationErrorCategory.TIMEOUT,
    ImageGenerationErrorCategory.RATE_LIMITED,
    ImageGenerationErrorCategory.SERVER,
)
