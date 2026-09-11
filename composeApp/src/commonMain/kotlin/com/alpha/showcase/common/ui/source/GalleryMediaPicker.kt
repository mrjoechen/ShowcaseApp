package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.utils.getMimeType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import androidx.compose.runtime.Composable
import createFilePickerDialogSettings
import getPlatform
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitPickerState
import io.github.vinceglb.filekit.dialogs.compose.PickerResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import com.alpha.showcase.common.utils.ToastUtil
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import isIos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.permission_required
import showcaseapp.composeapp.generated.resources.gallery_selection_access_required

sealed interface GalleryPickedMedia {
    data class File(val file: PlatformFile) : GalleryPickedMedia
    data class Asset(val media: GalleryMediaInput) : GalleryPickedMedia
}

suspend fun GalleryPickedMedia.toGalleryMediaInput(sourceName: String): GalleryMediaInput? = when (this) {
    is GalleryPickedMedia.File -> file.toGalleryMediaInput(sourceName)
    is GalleryPickedMedia.Asset -> media
}

/** iOS returns PhotoKit references; other platforms retain their existing file picker behavior. */
@Composable
fun rememberGalleryPickerLauncher(
    title: String,
    onProcessing: (Boolean) -> Unit,
    onResult: (List<GalleryPickedMedia>?) -> Unit,
): PickerResultLauncher {
    if (isIos()) {
        val scope = rememberCoroutineScope()
        val processing by rememberUpdatedState(onProcessing)
        val result by rememberUpdatedState(onResult)
        var picking by remember { mutableStateOf(false) }
        return remember(scope) {
            PickerResultLauncher {
                if (!picking) {
                    picking = true
                    scope.launch {
                        try {
                            result(pickGalleryAssets(processing)?.map { GalleryPickedMedia.Asset(it) })
                        } catch (error: CancellationException) {
                            processing(false)
                            throw error
                        } catch (_: GallerySelectionAccessException) {
                            processing(false)
                            ToastUtil.toast(Res.string.gallery_selection_access_required)
                        } catch (_: GalleryPermissionDeniedException) {
                            processing(false)
                            ToastUtil.toast(Res.string.permission_required)
                        } catch (error: Exception) {
                            processing(false)
                            ToastUtil.error(error.message ?: "Failed to select photos")
                        } finally {
                            picking = false
                        }
                    }
                }
            }
        }
    }
    return rememberFilePickerLauncher(
        type = getPlatform().galleryPickerType(),
        directory = getPlatform().directoryPickerInitialDirectory(),
        mode = FileKitMode.MultipleWithState(),
        dialogSettings = createFilePickerDialogSettings(title),
    ) { state ->
        when (state) {
            is FileKitPickerState.Started, is FileKitPickerState.Progress -> onProcessing(true)
            is FileKitPickerState.Completed -> onResult(state.result.map { GalleryPickedMedia.File(it) })
            is FileKitPickerState.Cancelled -> {
                onProcessing(false)
                onResult(null)
            }
            is FileKitPickerState.Failed -> {
                onProcessing(false)
                ToastUtil.error(state.cause.message ?: "Failed to load selected photos")
            }
        }
    }
}

/**
 * Normalize picker output for long-term storage:
 * - iOS uses GalleryPickedMedia.Asset and bypasses file normalization.
 * - Android keeps content uri and relies on persistable uri permission.
 */
suspend fun PlatformFile.toGalleryMediaInput(sourceName: String): GalleryMediaInput? {
    val rawUri = path.trim()
    if (rawUri.isBlank()) return null

    val displayName = name.ifBlank { rawUri.substringAfterLast('/') }
    val mimeType = resolveGalleryMimeType(this, displayName)
    val persistedUri = persistForGalleryIfNeeded(
        sourceName = sourceName,
        displayName = displayName,
        fallbackUri = rawUri,
    ) ?: return null

    return GalleryMediaInput(
        mediaUri = persistedUri,
        displayName = displayName,
        mimeType = mimeType,
    )
}

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

private fun resolveGalleryMimeType(file: PlatformFile, displayName: String): String {
    val byPlatform = runCatching { file.mimeType()?.toString() }
        .getOrNull()
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()

    if (byPlatform.startsWith("image/")) return byPlatform

    val byName = getMimeType(displayName).lowercase()
    if (byName.startsWith("image/")) return byName

    return "image/jpeg"
}

internal expect suspend fun PlatformFile.persistForGalleryIfNeeded(
    sourceName: String,
    displayName: String,
    fallbackUri: String,
): String?

internal expect fun resolveGalleryLocalPath(uri: String): String?
