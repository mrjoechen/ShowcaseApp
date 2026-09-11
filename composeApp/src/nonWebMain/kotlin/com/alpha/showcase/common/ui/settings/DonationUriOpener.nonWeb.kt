package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

@Composable
internal actual fun donationUriOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return handler::openUri
}
