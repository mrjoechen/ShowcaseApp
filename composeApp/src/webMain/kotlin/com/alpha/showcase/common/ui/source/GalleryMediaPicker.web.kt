package com.alpha.showcase.common.ui.source

import io.github.vinceglb.filekit.PlatformFile

// Browser picker paths are session-scoped virtual references, not reloadable local files.
actual fun isGalleryLocalFileMissing(uri: String): Boolean = true

internal actual suspend fun PlatformFile.persistForGalleryIfNeeded(
    sourceName: String,
    displayName: String,
    fallbackUri: String,
): String? = null

internal actual fun resolveGalleryLocalPath(uri: String): String? = null
