package com.alpha.showcase.common.ui.ai

import com.alpha.ai.imagegeneration.AiModelClient
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.showcase.common.ai.AiProfile

internal fun AiProfile.customName(client: AiModelClient): String? {
    val providerName = client.providerDescriptor(ProviderId(providerId))?.displayName ?: providerId
    // Recognize both automatic formats, including names left stale by earlier saves.
    return name.takeUnless { it.startsWith("$providerName · ") || it.endsWith(" · $providerName") }
}

internal fun AiProfile.displayName(client: AiModelClient): String {
    val providerName = client.providerDescriptor(ProviderId(providerId))?.displayName ?: providerId
    return customName(client) ?: "$model · $providerName"
}
