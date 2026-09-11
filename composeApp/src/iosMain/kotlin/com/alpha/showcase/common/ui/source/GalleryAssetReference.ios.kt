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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
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

internal fun PHAuthorizationStatus.asGalleryReadAccess(): GalleryReadAccess = when (this) {
    PHAuthorizationStatusNotDetermined -> GalleryReadAccess.NotDetermined
    PHAuthorizationStatusLimited -> GalleryReadAccess.Limited
    PHAuthorizationStatusAuthorized -> GalleryReadAccess.Full
    else -> GalleryReadAccess.Denied
}

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
        val selected = retryGallerySelectionOnAccessChange {
            selectGalleryImages(
                initialAccess = galleryReadAuthorization().asGalleryReadAccess(),
                requestAccess = {
                    suspendCancellableCoroutine { continuation ->
                        val completed: (PHAuthorizationStatus) -> Unit = { status ->
                            if (continuation.isActive) continuation.resume(status.asGalleryReadAccess())
                        }
                        if (iosMajorVersion >= 14) {
                            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite, completed)
                        } else {
                            PHPhotoLibrary.requestAuthorization(completed)
                        }
                    }
                },
                authorizedImages = ::authorizedGalleryImages,
                pickImages = {
                    val presenter = awaitGalleryPresenter()
                    if (iosMajorVersion >= 14) selectAssetIdentifiers(presenter)
                    else selectLegacyAssetIdentifier(presenter)
                },
                pickLimitedImages = ::selectLimitedGalleryImages,
            )
        }
        if (selected.isEmpty()) return@withContext null
        onProcessing(true)

        val accessible = accessibleGalleryAssetIdentifiers(selected)
        requireAllGalleryImagesAccessible(selected, accessible)
        withContext(Dispatchers.Default) {
            val result = PHAsset.fetchAssetsWithLocalIdentifiers(selected, null)
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
            requireAllGalleryImagesAccessible(selected, byIdentifier.keys)
            selected.map { byIdentifier.getValue(it) }
        }
    }

internal suspend fun authorizedGalleryImages(): List<String> = withContext(Dispatchers.Default) {
    if (!canReadGallery()) throw GalleryPermissionDeniedException()
    val assets = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options = null)
    List(assets.count.toInt()) { index ->
        (assets.objectAtIndex(index.toULong()) as PHAsset).localIdentifier
    }
}

internal fun galleryPresenter(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
    val window = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

internal suspend fun awaitGalleryPresenter(): UIViewController {
    repeat(100) {
        val controller = galleryPresenter()
        if (controller != null && controller.view.window != null &&
            !controller.isBeingDismissed() && !controller.isBeingPresented()) return controller
        delay(50)
    }
    error("Unable to present the photo library: previous page is still transitioning")
}

private suspend fun selectAssetIdentifiers(presenter: UIViewController): List<String> =
    suspendCancellableCoroutine { continuation ->
        val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 0
        }
        val picker = PHPickerViewController(config)
        var finished = false
        var removeForegroundObserver: () -> Unit = {}
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol,
            UIAdaptivePresentationControllerDelegateProtocol {
            fun complete(identifiers: List<String>) {
                if (finished) return
                finished = true
                removeForegroundObserver()
                // Finish dismissal before another authorization sheet or app loading is presented.
                picker.dismissViewControllerAnimated(true) {
                    activePickers.remove(this)
                    if (continuation.isActive) {
                        if (galleryReadAuthorization() != PHAuthorizationStatusAuthorized) {
                            continuation.resumeWith(Result.failure(GalleryAccessChangedException()))
                        } else continuation.resume(identifiers)
                    }
                }
            }
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                complete(didFinishPicking.filterIsInstance<PHPickerResult>()
                    .mapNotNull { it.assetIdentifier }.distinct())
            }
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
                if (finished) return
                finished = true
                removeForegroundObserver()
                activePickers.remove(this)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) {
            if (galleryReadAuthorization() != PHAuthorizationStatusAuthorized) delegate.complete(emptyList())
        }
        removeForegroundObserver = { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        activePickers.add(delegate)
        picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
        picker.presentationController?.delegate = delegate
        continuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                removeForegroundObserver()
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

internal suspend fun extendLimitedAccess(presenter: UIViewController) {
    var permissionSheet: UIViewController? = null
    try {
        if (iosMajorVersion >= 15) {
            suspendCancellableCoroutine<Unit> { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(presenter) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                permissionSheet = presenter.presentedViewController
            }
            while (permissionSheet != null && presenter.presentedViewController === permissionSheet) delay(50)
        } else {
            PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(presenter)
            // iOS 14 has no completion callback. Wait for its authorization sheet to close.
            delay(300)
            permissionSheet = presenter.presentedViewController
            while (presenter.presentedViewController === permissionSheet && permissionSheet != null) delay(100)
        }
    } catch (error: CancellationException) {
        withContext(NonCancellable + Dispatchers.Main) {
            permissionSheet?.dismissViewControllerAnimated(false, null)
        }
        throw error
    }
}
