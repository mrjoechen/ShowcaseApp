package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.theme.Dimen


@Composable
fun ConfigDialog(type: Int, editSource: RemoteApi? = null, onDismiss: (() -> Unit)? = null) {

    Dialog(
        properties = DialogProperties(),
        onDismissRequest = {
            onDismiss?.invoke()
        }
    ) {
        Surface(
            modifier = Modifier
                .padding(Dimen.spaceL)
                .sizeIn(minWidth = 300.dp, minHeight = 400.dp, maxWidth = 400.dp, maxHeight = 600.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 5.dp,
            shadowElevation = 9.dp
        ) {

            ConfigScreenTitle(type = type, editMode = editSource != null) {
                ConfigContent(type, editSource, onDismiss)
            }
        }
    }

}