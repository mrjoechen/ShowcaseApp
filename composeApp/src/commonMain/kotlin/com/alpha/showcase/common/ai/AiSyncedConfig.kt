package com.alpha.showcase.common.ai

/** The user-chosen AI configuration that syncs across devices; tasks and results stay local. */
internal data class AiSyncedConfig(
    val profiles: List<AiProfile>,
    val generationProfileId: String?,
    val understandingProfileId: String?,
    val styleKey: String,
    val facePrivacyEnabled: Boolean,
)

internal fun AiLibrary.syncedConfig(): AiSyncedConfig = AiSyncedConfig(
    profiles = activeProfiles,
    generationProfileId = generationProfileId,
    understandingProfileId = understandingProfileId,
    styleKey = styleKey,
    facePrivacyEnabled = facePrivacyEnabled,
)
