package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.cache.GalleryMediaRecord

private const val ASSET_PREFIX = "phasset://"

internal fun galleryAssetUri(identifier: String): String {
    require(identifier.isNotBlank())
    return ASSET_PREFIX + identifier
}

internal fun galleryAssetIdentifier(uri: String): String? =
    uri.takeIf { it.startsWith(ASSET_PREFIX) }?.removePrefix(ASSET_PREFIX)?.takeIf { it.isNotBlank() }

/** Recheck permissions on each read; never delete records because access can be restored. */
internal suspend fun filterAccessibleGalleryMedia(
    records: List<GalleryMediaRecord>,
    accessibleAssets: suspend (List<String>) -> Set<String> = ::accessibleGalleryAssetIdentifiers,
): List<GalleryMediaRecord> {
    val identifiers = records.mapNotNull { galleryAssetIdentifier(it.mediaUri) }.distinct()
    if (identifiers.isEmpty()) return records
    val accessible = accessibleAssets(identifiers)
    return records.filter { record ->
        galleryAssetIdentifier(record.mediaUri)?.let { it in accessible } ?: true
    }
}

internal expect suspend fun accessibleGalleryAssetIdentifiers(identifiers: List<String>): Set<String>

/** Native iOS selector returns metadata only, never an exported temporary file. */
internal expect suspend fun pickGalleryAssets(onProcessing: (Boolean) -> Unit): List<GalleryMediaInput>?

internal class GalleryPermissionDeniedException : Exception()
