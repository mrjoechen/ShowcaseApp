package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable

internal const val APP_SHARE_URL = "https://showcase.joechen.space"

@Composable
internal expect fun shareAppAction(): () -> Unit
