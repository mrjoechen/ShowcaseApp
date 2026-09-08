package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.view.SwitchItem
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.show_content_meta_info

@Composable
internal fun MediaMetadataSwitch(enabled: Boolean, onCheck: (Boolean) -> Unit) {
    SwitchItem(Icons.Outlined.Info, enabled, stringResource(Res.string.show_content_meta_info), onCheck)
}
