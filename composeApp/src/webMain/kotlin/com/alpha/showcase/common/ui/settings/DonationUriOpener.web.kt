package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import kotlinx.browser.window

/** Called directly from the click gesture so browsers allow the new tab. */
@Composable
internal actual fun donationUriOpener(): (String) -> Unit = {
    window.open(it, "_blank", "noopener,noreferrer")
}
