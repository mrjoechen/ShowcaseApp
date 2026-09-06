package com.alpha.ai.imagegeneration.internal.http

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.RetryAdvice
import kotlinx.coroutines.CancellationException

private const val CONNECTION_RESPONSE_LIMIT = 64L * 1024L

internal suspend fun HttpTransport.probeConnection(
    request: HttpRequest,
    providerId: ProviderId,
    capability: AiCapability,
    acceptedStatus: (Int) -> Boolean = { it in 200..299 },
): ProviderConnectionTestResult {
    val running = try {
        prepare(request, CONNECTION_RESPONSE_LIMIT).start()
    } catch (_: ImageGenerationTransportException) {
        return unavailable(ImageGenerationErrorCategory.NETWORK)
    }

    val response = try {
        running.await()
    } catch (error: CancellationException) {
        throw error
    } catch (_: ImageGenerationTransportException) {
        return unavailable(ImageGenerationErrorCategory.NETWORK)
    } finally {
        running.cancelIfActive()
    }

    if (acceptedStatus(response.status)) {
        return ProviderConnectionTestResult.Available(providerId, capability)
    }
    val category = when (response.status) {
        401, 403 -> ImageGenerationErrorCategory.AUTHENTICATION
        408 -> ImageGenerationErrorCategory.TIMEOUT
        429 -> ImageGenerationErrorCategory.RATE_LIMITED
        in 500..599 -> ImageGenerationErrorCategory.SERVER
        else -> ImageGenerationErrorCategory.CONFIGURATION
    }
    return unavailable(category, response.status)
}

private fun unavailable(
    category: ImageGenerationErrorCategory,
    httpStatus: Int? = null,
) = ProviderConnectionTestResult.Unavailable(
    ImageGenerationError(
        category = category,
        retryAdvice = if (category in setOf(
                ImageGenerationErrorCategory.NETWORK,
                ImageGenerationErrorCategory.TIMEOUT,
                ImageGenerationErrorCategory.RATE_LIMITED,
                ImageGenerationErrorCategory.SERVER,
            )
        ) {
            RetryAdvice.SAFE_TO_RETRY
        } else {
            RetryAdvice.DO_NOT_RETRY
        },
        requestMayHaveBeenAccepted = false,
        httpStatus = httpStatus,
        definitelyUnprocessed = true,
    ),
)
