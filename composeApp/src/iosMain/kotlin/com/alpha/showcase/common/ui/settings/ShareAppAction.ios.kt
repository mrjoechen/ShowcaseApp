@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.source.galleryPresenter
import platform.UIKit.*

@Composable
internal actual fun shareAppAction(): () -> Unit = {
    galleryPresenter()?.let { presenter ->
        val controller = UIActivityViewController(listOf(APP_SHARE_URL), null)
        controller.popoverPresentationController?.let {
            it.sourceView = presenter.view
            it.sourceRect = presenter.view.bounds
            it.permittedArrowDirections = 0uL
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}
