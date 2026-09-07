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
)

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
