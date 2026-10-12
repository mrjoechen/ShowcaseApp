package com.alpha.showcase.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import usesRemoteNavigation

/** Place a TV field action outside the text input's focus and keyboard ownership. */
@Composable
internal fun TvTextFieldWithAction(
    enabled: Boolean = usesRemoteNavigation(),
    action: @Composable () -> Unit,
    field: @Composable (Modifier, (@Composable () -> Unit)?) -> Unit,
) {
    if (enabled) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.weight(1f), null)
            Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) { action() }
        }
    } else {
        field(Modifier.fillMaxWidth(), action)
    }
}
