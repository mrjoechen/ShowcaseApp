package com.alpha.showcase.common.ui.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun shareAppAction(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, APP_SHARE_URL)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
