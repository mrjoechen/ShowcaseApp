package com.alpha.showcase.common.ai

import kotlinx.serialization.json.Json
import kotlin.test.*

class AiAttemptHistoryTest {
    private val queued = AiTask("task", "profile", 1, "ghibli", "prompt", 100, "source.png", "image/png")
    @Test fun retriesPreservePreviousAttemptAndUpdateOnlyTheCurrentAttempt() {
        val running = queued.recordChange(queued.copy(status = AiTaskStatus.RUNNING, attempt = 1), 200)
        val failed = running.recordChange(running.copy(status = AiTaskStatus.RETRY_WAIT, errorCategory = "RATE_LIMIT"), 300)
        val retry = failed.recordChange(failed.copy(status = AiTaskStatus.RUNNING, attempt = 2, errorCategory = null), 400)
        val done = retry.recordChange(retry.copy(status = AiTaskStatus.SUCCEEDED, resultFile = "result.png"), 500)
        assertEquals(2, done.attempts.size)
        assertEquals(failed.attempts.single(), done.attempts.first())
        assertEquals(400L, done.attempts.last().startedAt)
        assertEquals(500L, done.attempts.last().updatedAt)
        assertEquals(AiTaskStatus.SUCCEEDED, done.attempts.last().status)
    }
    @Test fun existingTasksWithoutHistoryRemainReadable() {
        val json = """{"id":"task","profileId":"profile","profileRevision":1,"styleKey":"ghibli","prompt":"prompt","createdAt":100,"sourceFile":"source.png","sourceMimeType":"image/png"}"""
        val decoded = Json.decodeFromString<AiTask>(json)
        assertEquals(queued, decoded)
        assertTrue(decoded.attempts.isEmpty())
    }
}
