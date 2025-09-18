package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.HintText
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.path

@Composable
fun FilePathSelector(
    fileApi: RcloneRemoteApi?,
    path: String,
    onPathChange: (String) -> Unit,
) {
    var showFolderPicker by rememberSaveable(fileApi) { mutableStateOf(false) }

    OutlinedTextField(
        shape = RoundedCornerShape(Dimen.textFiledCorners),
        label = {
            Text(
                text = stringResource(Res.string.path),
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
        },
        value = path,
        onValueChange = onPathChange,
        placeholder = { HintText(text = "/") },
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { showFolderPicker = true },
                enabled = fileApi != null // 只有在FTP配置完成后才能选择
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = stringResource(R.string.choose_foder),
                    tint = if (fileApi != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        },
        readOnly = false // 允许手动输入
    )
    
    if (showFolderPicker && fileApi != null) {
        NetworkFolderPickerDialog(
            showDialog = true,
            remoteApi = fileApi,
            initialPath = path.ifEmpty { "/" },
            onDismiss = { showFolderPicker = false },
            onPathSelected = { selectedPath ->
                onPathChange(selectedPath)
                showFolderPicker = false
            }
        )
    }
}