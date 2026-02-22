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
import com.alpha.showcase.common.utils.decodeUrlPath

import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.choose_folder
import showcaseapp.composeapp.generated.resources.path

@Composable
fun FilePathSelector(
    fileApi: RcloneRemoteApi?,
    path: String,
    onPathChange: (String) -> Unit,
) {
    var showFolderPicker by rememberSaveable(fileApi) { mutableStateOf(false) }

    val displayPath = try { decodeUrlPath(path) } catch (_: Exception) { path }

    OutlinedTextField(
        shape = RoundedCornerShape(Dimen.textFiledCorners),
        label = {
            Text(
                text = stringResource(Res.string.path),
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
        },
        value = displayPath,
        onValueChange = onPathChange,
        placeholder = { HintText(text = "/") },
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { showFolderPicker = true },
                enabled = fileApi != null
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = stringResource(Res.string.choose_folder),
                    tint = if (fileApi != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        },
        readOnly = false
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