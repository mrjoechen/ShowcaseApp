@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private var summaryArchivePickerDelegate: NSObject? = null

internal actual fun prepareSummaryFileDialogs() = Unit

/** FileKit 0.16 can deliver both UIDocumentPicker delegate callbacks on newer iOS versions. */
internal actual suspend fun openSummaryArchivePicker(): PlatformFile? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        var finished = false
        fun finish(result: PlatformFile?) {
            if (finished) return
            finished = true
            summaryArchivePickerDelegate = null
            if (continuation.isActive) continuation.resume(result)
        }

        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
                finish(PlatformFile(didPickDocumentAtURL))
            }

            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                finish((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::PlatformFile))
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                finish(null)
            }
        }
        summaryArchivePickerDelegate = delegate

        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(requireNotNull(UTType.typeWithFilenameExtension("scsummary"))),
        )
        picker.allowsMultipleSelection = false
        picker.delegate = delegate
        val presenter = activeSummaryPickerPresenter()
        if (presenter == null) {
            finish(null)
            return@suspendCancellableCoroutine
        }
        presenter.presentViewController(picker, animated = true, completion = null)
        continuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                finish(null)
                picker.dismissViewControllerAnimated(false, completion = null)
            }
        }
    }
}

private fun activeSummaryPickerPresenter(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
    val window = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
