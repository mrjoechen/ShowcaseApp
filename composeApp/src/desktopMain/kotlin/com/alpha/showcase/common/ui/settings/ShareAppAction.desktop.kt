package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.alpha.showcase.common.utils.ToastUtil
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_link_copied

@Suppress("DEPRECATION")
@Composable
internal actual fun shareAppAction(): () -> Unit {
    val clipboard = LocalClipboardManager.current
    val message = stringResource(Res.string.app_link_copied)
    return {
        clipboard.setText(AnnotatedString(APP_SHARE_URL))
        ToastUtil.success(message)
    }
}
