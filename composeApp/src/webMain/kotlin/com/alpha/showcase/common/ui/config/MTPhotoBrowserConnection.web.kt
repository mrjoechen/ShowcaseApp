package com.alpha.showcase.common.ui.config

import kotlinx.browser.window

internal actual fun currentMTPhotoBrowserPageProtocol(): String? = window.location.protocol
