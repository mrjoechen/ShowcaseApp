@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ui.source

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import coil3.compose.AsyncImage
import com.alpha.showcase.common.theme.AppTheme
import com.alpha.showcase.common.ui.ai.AiPage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserverProtocol
import platform.Photos.PHChange
import platform.darwin.NSObject
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import showcaseapp.composeapp.generated.resources.*
import kotlin.coroutines.resume

internal suspend fun selectLimitedGalleryImages(): List<String> {
    val presenter = awaitGalleryPresenter()
    val images = authorizedGalleryImages()
    return selectAuthorizedImages(presenter, images)
}

private suspend fun selectAuthorizedImages(
    presenter: UIViewController, initialImages: List<String>,
): List<String> = suspendCancellableCoroutine { continuation ->
    var finished = false
    lateinit var controller: UIViewController
    fun complete(result: List<String> = emptyList(), error: Exception? = null) {
        if (finished) return
        finished = true
        controller.dismissViewControllerAnimated(true) {
            if (continuation.isActive) {
                if (error != null) continuation.resumeWith(Result.failure(error))
                else continuation.resume(result)
            }
        }
    }
    controller = ComposeUIViewController {
        AppTheme {
            var selected by remember { mutableStateOf(emptySet<String>()) }
            var images by remember { mutableStateOf(initialImages) }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            suspend fun refresh() {
                when (galleryReadAuthorization().asGalleryReadAccess()) {
                    GalleryReadAccess.Full -> complete(error = GalleryAccessChangedException())
                    GalleryReadAccess.Limited -> {
                        images = authorizedGalleryImages()
                        selected = retainAuthorizedGallerySelection(selected, images)
                    }
                    else -> complete(error = GalleryPermissionDeniedException())
                }
            }
            fun refreshAfterResume() {
                if (!busy && !finished) scope.launch {
                    try { refresh() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { complete(error = error) }
                }
            }
            DisposableEffect(Unit) {
                val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                    UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
                ) { refreshAfterResume() }
                val photoObserver = object : NSObject(), PHPhotoLibraryChangeObserverProtocol {
                    override fun photoLibraryDidChange(changeInstance: PHChange) {
                        dispatch_async(dispatch_get_main_queue()) { refreshAfterResume() }
                    }
                }
                PHPhotoLibrary.sharedPhotoLibrary().registerChangeObserver(photoObserver)
                onDispose {
                    NSNotificationCenter.defaultCenter.removeObserver(observer)
                    PHPhotoLibrary.sharedPhotoLibrary().unregisterChangeObserver(photoObserver)
                }
            }
            LaunchedEffect(Unit) { refreshAfterResume() }
            AiPage(
                title = stringResource(Res.string.select_photos),
                onBack = { if (!busy) complete() },
                actions = {
                    TextButton(enabled = !busy && selected.isNotEmpty(), onClick = {
                        complete(selected.toList())
                    }) { Text(stringResource(Res.string.confirm)) }
                },
            ) {
                Text(
                    stringResource(Res.string.gallery_limited_selection_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            // Keep this list mounted; the system sheet is presented on top of it.
                            if (galleryReadAuthorization().asGalleryReadAccess() == GalleryReadAccess.Limited) {
                                extendLimitedAccess(controller)
                            }
                            refresh()
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { complete(error = error) }
                        finally { busy = false }
                    }
                }) {
                    Text(stringResource(Res.string.gallery_select_more_photos))
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(images, key = { it }) { identifier ->
                        val checked = identifier in selected
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier.aspectRatio(1f).clip(shape)
                                .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                                .toggleable(checked, enabled = !busy, role = Role.Checkbox) {
                                    selected = if (it) selected + identifier else selected - identifier
                                },
                        ) {
                            AsyncImage(
                                model = galleryAssetUri(identifier), contentDescription = stringResource(Res.string.select_photos),
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = if (checked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                                border = BorderStroke(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else Color.White),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (checked) Icon(
                                        Icons.Default.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Explicit cancel avoids interactive dismissal leaving the suspended operation unfinished.
    controller.modalPresentationStyle = UIModalPresentationFullScreen
    presenter.presentViewController(controller, true, null)
    continuation.invokeOnCancellation {
        dispatch_async(dispatch_get_main_queue()) {
            finished = true
            controller.dismissViewControllerAnimated(false, null)
        }
    }
}
