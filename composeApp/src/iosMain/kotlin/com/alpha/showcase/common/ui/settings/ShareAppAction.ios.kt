@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ui.settings

import PLATFORM_TYPE
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.alpha.showcase.common.ui.source.galleryPresenter
import platform.UIKit.*

// The App Store edition, Showcase Pix, is shared through its own overview page.
@Composable
internal actual fun shareAppAction(): () -> Unit = {
    galleryPresenter()?.let { presenter ->
        val shareUrl = documentationBaseUrl(PLATFORM_TYPE.Ios, Locale.current.language) + "/"
        val controller = UIActivityViewController(listOf(shareUrl), null)
        controller.popoverPresentationController?.let {
            it.sourceView = presenter.view
            it.sourceRect = presenter.view.bounds
            it.permittedArrowDirections = 0uL
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}
