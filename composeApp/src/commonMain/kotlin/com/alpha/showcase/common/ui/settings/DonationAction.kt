package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

private const val DONATION_URL = "https://showcase.joechen.space/donate.html"

@Composable
internal fun donationAction(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return {
        try {
            uriHandler.openUri(DONATION_URL)
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }
}
