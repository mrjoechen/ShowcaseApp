package com.alpha.showcase.common.cache

internal actual suspend fun loadPersistedGalleryMediaInputs(
    sourceName: String,
): List<GalleryMediaInput> = emptyList()

internal actual suspend fun deletePersistedGalleryLocalFileIfNeeded(
    sourceName: String,
    mediaUri: String,
) = Unit
