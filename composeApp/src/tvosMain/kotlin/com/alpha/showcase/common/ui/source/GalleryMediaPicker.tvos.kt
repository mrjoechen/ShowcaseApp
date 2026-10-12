package com.alpha.showcase.common.ui.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alpha.showcase.common.cache.GalleryMediaInput

// tvOS has no photo library or document picker. Source selection hides both source types.
@Composable
actual fun rememberGalleryPickerLauncher(
    title: String,
    onProcessing: (Boolean) -> Unit,
    onResult: (List<GalleryPickedMedia>?) -> Unit,
): GalleryPickerLauncher = remember(onResult) { GalleryPickerLauncher { onResult(null) } }

@Composable
actual fun rememberSourceDirectoryPicker(title: String, onResult: (String?) -> Unit): GalleryPickerLauncher =
    remember(onResult) { GalleryPickerLauncher { onResult(null) } }

actual fun isGalleryLocalFileMissing(uri: String): Boolean = true
internal actual fun resolveGalleryLocalPath(uri: String): String? = null
internal actual suspend fun accessibleGalleryAssetIdentifiers(identifiers: List<String>): Set<String> = emptySet()
internal actual suspend fun pickGalleryAssets(onProcessing: (Boolean) -> Unit): List<GalleryMediaInput>? = null
