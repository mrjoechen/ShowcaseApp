package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import kotlinx.browser.window

/** Same-tab navigation survives popup blockers after the celebration delay. */
@Composable
internal actual fun donationUriOpener(): (String) -> Unit = { window.location.href = it }
