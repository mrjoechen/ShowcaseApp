package com.alpha.showcase.common.ai

import kotlin.test.*

class AiPolicyTest {
    @Test fun interruptedRequestsAreNeverAutomaticallyResubmitted() {
        assertEquals(AiTaskStatus.RESULT_UNKNOWN, AiTaskStatus.RUNNING.afterRestart())
        assertEquals(AiTaskStatus.QUEUED, AiTaskStatus.QUEUED.afterRestart())
        assertEquals(AiTaskStatus.QUEUED, AiTaskStatus.RETRY_WAIT.afterRestart())
        assertEquals(AiTaskStatus.SUCCEEDED, AiTaskStatus.SUCCEEDED.afterRestart())
    }

    @Test fun browserCannotShowOrRequestAiEvenWithSavedSettings() {
        assertFalse(aiFeaturesAvailable(isBrowser = true))
        assertTrue(aiFeaturesAvailable(isBrowser = false))
    }

    @Test fun profileOriginChangesRequireANewToken() {
        assertTrue(canReuseAiCredential("openai", "https://api.example/v1", "openai", "https://api.example/v2"))
        assertFalse(canReuseAiCredential("openai", "https://api.example/v1", "openai", "https://other.example/v1"))
        assertFalse(canReuseAiCredential("openai", "https://api.example/v1", "openai-vision", "https://api.example/v1"))
        assertFalse(canReuseAiCredential("openai", "https://api.example", "openai", "http://api.example"))
    }
}
