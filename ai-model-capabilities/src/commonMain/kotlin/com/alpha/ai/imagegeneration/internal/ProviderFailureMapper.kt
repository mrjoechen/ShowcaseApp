package com.alpha.ai.imagegeneration.internal

import com.alpha.ai.imagegeneration.IdempotencySupport
import com.alpha.ai.imagegeneration.ProviderCapabilities
import com.alpha.ai.imagegeneration.RequestPhase
import com.alpha.ai.imagegeneration.RetryAdvice

internal object ProviderFailureMapper {
    fun merge(
        capabilities: ProviderCapabilities,
        providerAdvice: RetryAdvice,
        phase: RequestPhase,
        callerHasIdempotencyKey: Boolean = false,
        definitelyUnprocessed: Boolean = false,
        httpStatus: Int? = null,
    ): RetryAdvice {
        if (providerAdvice != RetryAdvice.SAFE_TO_RETRY) return providerAdvice

        val explicitlyRejected = definitelyUnprocessed && httpStatus in capabilities.safeRejectedHttpStatuses
        if (explicitlyRejected) return RetryAdvice.SAFE_TO_RETRY

        if (phase !in capabilities.safeRetryPhases) {
            return if (phase == RequestPhase.IN_FLIGHT) RetryAdvice.AMBIGUOUS else RetryAdvice.DO_NOT_RETRY
        }

        val idempotencyIsAvailable = capabilities.idempotencySupport == IdempotencySupport.PROVIDER_NATIVE ||
            (capabilities.idempotencySupport == IdempotencySupport.CALLER_KEY && callerHasIdempotencyKey)
        return if (phase == RequestPhase.IN_FLIGHT && !idempotencyIsAvailable) {
            RetryAdvice.AMBIGUOUS
        } else {
            RetryAdvice.SAFE_TO_RETRY
        }
    }
}
