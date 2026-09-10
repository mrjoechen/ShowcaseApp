@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.utils.getMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Photos.*
import platform.PhotosUI.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private val activePickers = mutableSetOf<NSObject>()
private val iosMajorVersion: Int
    get() = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 13

internal fun galleryReadAuthorization(): PHAuthorizationStatus =
    if (iosMajorVersion >= 14) PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    else PHPhotoLibrary.authorizationStatus()

private fun canReadGallery() = galleryReadAuthorization().let {
    it == PHAuthorizationStatusAuthorized || it == PHAuthorizationStatusLimited
}

internal actual suspend fun accessibleGalleryAssetIdentifiers(identifiers: List<String>): Set<String> =
    withContext(Dispatchers.Default) {
        if (!canReadGallery() || identifiers.isEmpty()) return@withContext emptySet()
        buildSet {
            identifiers.distinct().chunked(200).forEach { batch ->
                val assets = PHAsset.fetchAssetsWithLocalIdentifiers(batch, options = null)
                for (index in 0 until assets.count.toInt()) {
                    val asset = assets.objectAtIndex(index.toULong()) as PHAsset
                    if (asset.mediaType == PHAssetMediaTypeImage) add(asset.localIdentifier)
                }
            }
        }
    }

internal actual suspend fun pickGalleryAssets(onProcessing: (Boolean) -> Unit): List<GalleryMediaInput>? =
    withContext(Dispatchers.Main) {
        if (galleryReadAuthorization() == PHAuthorizationStatusNotDetermined) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val completed: (PHAuthorizationStatus) -> Unit = {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                if (iosMajorVersion >= 14) {
                    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite, completed)
                } else {
                    PHPhotoLibrary.requestAuthorization(completed)
                }
            }
        }
        if (!canReadGallery()) throw GalleryPermissionDeniedException()
        val presenter = galleryPresenter() ?: error("Unable to present the photo library")
        val selected = if (iosMajorVersion >= 14) selectAssetIdentifiers(presenter)
            else selectLegacyAssetIdentifier(presenter)
        if (selected.isEmpty()) return@withContext null
        onProcessing(true)

        // PHPicker selection itself does not extend limited PhotoKit authorization.
        var accessible = accessibleGalleryAssetIdentifiers(selected)
        if (accessible.size < selected.size && galleryReadAuthorization() == PHAuthorizationStatusLimited) {
            onProcessing(false)
            extendLimitedAccess(presenter)
            onProcessing(true)
            accessible = accessibleGalleryAssetIdentifiers(selected)
        }
        if (accessible.isEmpty()) throw GalleryPermissionDeniedException()
        withContext(Dispatchers.Default) {
            val result = PHAsset.fetchAssetsWithLocalIdentifiers(selected.filter { it in accessible }, null)
            val byIdentifier = buildMap {
                for (index in 0 until result.count.toInt()) {
                    val asset = result.objectAtIndex(index.toULong()) as PHAsset
                    if (asset.mediaType != PHAssetMediaTypeImage) continue
                    val resource = PHAssetResource.assetResourcesForAsset(asset)
                        .filterIsInstance<PHAssetResource>().firstOrNull()
                    val name = resource?.originalFilename ?: "${asset.localIdentifier}.jpg"
                    put(asset.localIdentifier, GalleryMediaInput(
                        mediaUri = galleryAssetUri(asset.localIdentifier),
                        displayName = name,
                        mimeType = getMimeType(name).takeIf { it.startsWith("image/") } ?: "image/jpeg",
                    ))
                }
            }
            selected.mapNotNull { byIdentifier[it] }
        }
    }

private fun galleryPresenter(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
    val window = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

private suspend fun selectAssetIdentifiers(presenter: UIViewController): List<String> =
    suspendCancellableCoroutine { continuation ->
        val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 0
        }
        val picker = PHPickerViewController(config)
        var finished = false
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol,
            UIAdaptivePresentationControllerDelegateProtocol {
            fun complete(identifiers: List<String>) {
                if (finished) return
                finished = true
                // Finish dismissal before another authorization sheet or app loading is presented.
                picker.dismissViewControllerAnimated(true) {
                    activePickers.remove(this)
                    if (continuation.isActive) continuation.resume(identifiers)
                }
            }
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                complete(didFinishPicking.filterIsInstance<PHPickerResult>()
                    .mapNotNull { it.assetIdentifier }.distinct())
            }
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
                if (finished) return
                finished = true
                activePickers.remove(this)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
        activePickers.add(delegate)
        picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
        picker.presentationController?.delegate = delegate
        continuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                activePickers.remove(delegate)
                picker.dismissViewControllerAnimated(false, null)
            }
        }
    }

// Keep iOS 13 supported: PHPicker is available starting in iOS 14.
private suspend fun selectLegacyAssetIdentifier(presenter: UIViewController): List<String> =
    suspendCancellableCoroutine { continuation ->
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        }
        var finished = false
        val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol,
            UINavigationControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
            fun complete(identifier: String?) {
                if (finished) return
                finished = true
                picker.dismissViewControllerAnimated(true) {
                    activePickers.remove(this)
                    if (continuation.isActive) continuation.resume(listOfNotNull(identifier))
                }
            }
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                complete((didFinishPickingMediaWithInfo[UIImagePickerControllerPHAsset] as? PHAsset)?.localIdentifier)
            }
            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) = complete(null)
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
                if (finished) return
                finished = true
                activePickers.remove(this)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
        activePickers.add(delegate)
        picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
        picker.presentationController?.delegate = delegate
        continuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                activePickers.remove(delegate)
                picker.dismissViewControllerAnimated(false, null)
            }
        }
    }

private suspend fun extendLimitedAccess(presenter: UIViewController) {
    var permissionSheet: UIViewController? = null
    try {
        if (iosMajorVersion >= 15) {
            suspendCancellableCoroutine<Unit> { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(presenter) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                permissionSheet = presenter.presentedViewController
            }
        } else {
            PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(presenter)
            permissionSheet = presenter.presentedViewController
            // iOS 14 has no completion callback. Wait for its authorization sheet to close.
            delay(300)
            while (presenter.presentedViewController === permissionSheet && permissionSheet != null) delay(100)
        }
    } catch (error: CancellationException) {
        withContext(NonCancellable + Dispatchers.Main) {
            permissionSheet?.dismissViewControllerAnimated(false, null)
        }
        throw error
    }
}
