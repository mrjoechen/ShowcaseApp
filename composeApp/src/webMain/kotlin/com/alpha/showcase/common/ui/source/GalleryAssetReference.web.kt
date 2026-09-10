package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput

internal actual suspend fun accessibleGalleryAssetIdentifiers(identifiers: List<String>): Set<String> = emptySet()
internal actual suspend fun pickGalleryAssets(onProcessing: (Boolean) -> Unit): List<GalleryMediaInput>? =
    error("PhotoKit selection is only available on iOS")
