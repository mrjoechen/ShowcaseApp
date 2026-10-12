package com.alpha.showcase.common.ui.source

import androidx.compose.runtime.Composable
import com.alpha.showcase.common.cache.GalleryMediaInput

interface GalleryPickedMedia {
    suspend fun readMedia(sourceName: String): GalleryMediaInput?
    data class Asset(val media: GalleryMediaInput) : GalleryPickedMedia {
        override suspend fun readMedia(sourceName: String): GalleryMediaInput = media
    }
}

suspend fun GalleryPickedMedia.toGalleryMediaInput(sourceName: String): GalleryMediaInput? = readMedia(sourceName)

fun interface GalleryPickerLauncher { fun launch() }

@Composable
expect fun rememberGalleryPickerLauncher(
    title: String,
    onProcessing: (Boolean) -> Unit,
    onResult: (List<GalleryPickedMedia>?) -> Unit,
): GalleryPickerLauncher

@Composable
expect fun rememberSourceDirectoryPicker(title: String, onResult: (String?) -> Unit): GalleryPickerLauncher

fun toGalleryDisplayUri(uri: String): String {
    val normalized = uri.trim()
    if (normalized.isBlank()) return normalized
    if (galleryAssetIdentifier(normalized) != null) return normalized
    if (normalized.startsWith("content://", ignoreCase = true)) return normalized

    val resolvedLocalPath = resolveGalleryLocalPath(normalized)
    if (resolvedLocalPath != null) {
        return if (resolvedLocalPath.startsWith("/")) {
            "file://$resolvedLocalPath"
        } else {
            resolvedLocalPath
        }
    }

    if (normalized.startsWith("file://", ignoreCase = true)) return normalized
    return if (normalized.startsWith("/")) "file://$normalized" else normalized
}

expect fun isGalleryLocalFileMissing(uri: String): Boolean

internal expect fun resolveGalleryLocalPath(uri: String): String?
