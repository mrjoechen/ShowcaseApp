package com.alpha.showcase.common.ui.source

import kotlinx.serialization.Serializable

@Serializable
internal data class SourceAdditionFeedback(
    val hasAddedSource: Boolean = false,
    val latestSourceName: String? = null,
    val celebrationPending: Boolean = false,
) {
    fun added(name: String) = copy(
        hasAddedSource = true,
        latestSourceName = name,
        celebrationPending = celebrationPending || !hasAddedSource,
    )

    fun opened(name: String) = if (latestSourceName == name) copy(latestSourceName = null) else this
    fun celebrated() = copy(celebrationPending = false)
    fun renamed(previous: String, replacement: String) =
        if (latestSourceName == previous) copy(latestSourceName = replacement) else this
}
