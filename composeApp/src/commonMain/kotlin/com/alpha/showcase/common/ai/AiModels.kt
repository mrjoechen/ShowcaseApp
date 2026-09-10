package com.alpha.showcase.common.ai

import io.ktor.http.Url
import kotlinx.serialization.Serializable

internal fun aiFeaturesAvailable(isBrowser: Boolean): Boolean = !isBrowser

@Serializable
enum class AiTaskStatus(val terminal: Boolean) {
    QUEUED(false), RUNNING(false), RETRY_WAIT(false), SUCCEEDED(true), FAILED(true),
    RESULT_UNKNOWN(true), CANCELLED(true), NEEDS_USER_ACTION(true);

    fun afterRestart(): AiTaskStatus = when (this) {
        RUNNING -> RESULT_UNKNOWN
        RETRY_WAIT -> QUEUED // Only proven-unprocessed failures enter RETRY_WAIT.
        else -> this
    }
}

@Serializable
data class AiProfile(
    val id: String,
    val revision: Int = 1,
    val name: String,
    val providerId: String,
    val model: String,
    val baseUrl: String,
    val encryptedToken: String,
    val allowInsecureHttp: Boolean = false,
    val archived: Boolean = false,
) {
    override fun toString(): String = "AiProfile(id=$id, revision=$revision, credentials=<redacted>)"
}

@Serializable
data class AiTask(
    val id: String,
    val profileId: String,
    val profileRevision: Int,
    val styleKey: String,
    val prompt: String,
    val createdAt: Long,
    val sourceFile: String,
    val sourceMimeType: String,
    val status: AiTaskStatus = AiTaskStatus.QUEUED,
    val stage: String = "PREPARING_INPUT",
    val attempt: Int = 0,
    val resultFile: String? = null,
    val errorCategory: String? = null,
    val updatedAt: Long = createdAt,
    val attempts: List<AiAttempt> = emptyList(),
    val originalFile: String? = null,
    val originalName: String? = null,
)

/** A compact durable attempt history; never stores tokens or provider response bodies. */
@Serializable
data class AiAttempt(
    val number: Int,
    val status: AiTaskStatus,
    val stage: String,
    val startedAt: Long,
    val updatedAt: Long,
    val errorCategory: String? = null,
)

internal fun AiTask.recordChange(next: AiTask, timestamp: Long): AiTask {
    if (next == this) return this
    val history = attempts.toMutableList()
    if (next.attempt > 0) {
        val previous = history.lastOrNull()?.takeIf { it.number == next.attempt }
        val snapshot = AiAttempt(next.attempt, next.status, next.stage,
            previous?.startedAt ?: timestamp, timestamp, next.errorCategory)
        if (previous != null) history[history.lastIndex] = snapshot else history.add(snapshot)
    }
    return next.copy(updatedAt = timestamp, attempts = history)
}

@Serializable
data class AiSummaryContent(
    val summary: String,
    val narration: String,
    val tags: List<String>,
)

data class AiSummaryState(
    val content: AiSummaryContent? = null,
    val generating: Boolean = false,
    val failed: Boolean = false,
    val facePrivacyPending: Boolean = false,
    val facePrivacyBlocked: Boolean = false,
    val facePrivacyUnavailable: Boolean = false,
)

@Serializable
data class AiLibrary(
    val profiles: List<AiProfile> = emptyList(),
    val generationProfileId: String? = null,
    val understandingProfileId: String? = null,
    val styleKey: String = "ghibli",
    val tasks: List<AiTask> = emptyList(),
    val summaries: Map<String, AiSummaryContent> = emptyMap(),
    val facePrivacyEnabled: Boolean = false,
) {
    val activeProfiles: List<AiProfile> get() = profiles.filterNot { it.archived }
    fun profile(task: AiTask): AiProfile? = profiles.firstOrNull {
        it.id == task.profileId && it.revision == task.profileRevision
    }
}

internal fun canReuseAiCredential(oldProvider: String, oldUrl: String, provider: String, url: String): Boolean =
    oldProvider == provider && runCatching {
        val old = Url(oldUrl)
        val new = Url(url)
        old.protocol == new.protocol && old.host.equals(new.host, ignoreCase = true) && old.port == new.port
    }.getOrDefault(false)

internal fun isValidAiBaseUrl(value: String, allowInsecureHttp: Boolean): Boolean = runCatching {
    val url = Url(value)
    url.host.isNotBlank() && (url.protocol.name == "https" || (allowInsecureHttp && url.protocol.name == "http")) &&
        url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty()
}.getOrDefault(false)
