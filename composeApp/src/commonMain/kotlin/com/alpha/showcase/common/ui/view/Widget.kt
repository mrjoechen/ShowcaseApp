package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.utils.ToastUtil
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.hide_password
import showcaseapp.composeapp.generated.resources.password
import showcaseapp.composeapp.generated.resources.path
import showcaseapp.composeapp.generated.resources.show_password


@Composable
fun PasswordInput(
    modifier: Modifier = Modifier,
    password: String,
    passwordVisible: Boolean,
    editMode: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibleChanged: (Boolean) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        label = {
            Text(
                text = stringResource(Res.string.password),
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
        },
        value = password,
        onValueChange = { onPasswordChange(it) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        placeholder = { Text(text = "") },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (!editMode) {
                if (passwordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff
            } else {
                if (password.isNotBlank()) {
                    Icons.Filled.Close
                } else {
                    null
                }
            }

            val description =
                if (passwordVisible) stringResource(Res.string.hide_password) else stringResource(
                    Res.string.show_password
                )
            IconButton(onClick = {
                if (!editMode) {
                    onPasswordVisibleChanged(!passwordVisible)
                } else {
                    onPasswordChange("")
                }
            }) {
                image?.let {
                    Icon(imageVector = image, description)
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun DropdownSelection(
    label: String = "",
    placeholder: String = "",
    supportingText: (@Composable () -> Unit)? = null,
    headItem: (@Composable () -> Unit)? = null,
    selectIndex: Int = -1,
    list: List<String> = listOf(),
    onSelect: ((Int) -> Unit)? = null,
    onSelectNext: ((Int) -> Unit)? = null
) {
    val options by rememberUpdatedState(newValue = list)
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember(key1 = list) { mutableStateOf(if (options.isEmpty() || selectIndex == -1) "" else options[selectIndex]) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {

        OutlinedTextField(
            // The `menuAnchor` modifier must be passed to the text field for correctness.
            modifier = Modifier
                .clickable(false, onClick = { })
                .menuAnchor(),
            readOnly = true,
            value = selectedOptionText,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            supportingText = supportingText,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None)
        )

        ExposedDropdownMenu(
            modifier = Modifier.heightIn(max = 256.dp),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            headItem?.invoke()
            options.forEachIndexed { index, selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        selectedOptionText = selectionOption
                        expanded = false
                        onSelect?.invoke(index)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                selectedOptionText = selectionOption
                                onSelect?.invoke(index)
                                onSelectNext?.invoke(index)
                            }
                        ){
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "enter")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SelectPathDropdown(remote: RcloneRemoteApi?, filter: ((String) -> Boolean)? = null, initPathList: (suspend (RcloneRemoteApi, String) -> Result<List<NetworkFile>>)? = null, onSelectPath: (suspend (RcloneRemoteApi, String) -> Result<List<NetworkFile>>)? = null) {

    val scope = rememberCoroutineScope()

    val pathList = remember(remote) {
        mutableStateListOf<String>()
    }
    var loadingPathList by remember { mutableStateOf(false) }
    var path by rememberSaveable(key = "path") {
        mutableStateOf(remote?.path ?: "")
    }

    var parentPath by rememberSaveable(key = "parentPath") {
        mutableStateOf(
            remote?.let {
                if (it.path.contains("/") && it.path != "/") {
                    it.path.substring(0, it.path.lastIndexOf("/"))
                } else {
                    ""
                }
            }?:""
        )
    }


    var selectIndex by remember(remote) {
        mutableIntStateOf(pathList.indexOf(path.substring(path.lastIndexOf("/") + 1)))
    }

    LaunchedEffect(key1 = remote){
        remote?.let {
            // 当前目录的父目录
            val tempPath = if (it.path.contains("/") && it.path != "/") {
                it.path.substring(0, it.path.lastIndexOf("/"))
            } else {
                ""
            }
            initPathList?.invoke(it, tempPath)?.let { result ->
                result.onSuccess {
                    val paths = it.filter { networkFile ->
                        filter?.invoke("${if (tempPath.isBlank()) "" else "$tempPath/"}${networkFile.fileName}")
                            ?: true
                    }.map { networkFile ->
                        networkFile.fileName
                    }.toList()

                    if (pathList.isEmpty()) {
                        pathList.addAll(paths)
                    }
                }

                result.onFailure {
                    ToastUtil.error("Error: ${it.message}")
                    pathList.clear()
                    pathList.add("/")
                    parentPath = ""
                }
            }
        }

        selectIndex = pathList.indexOf(path.substring(path.lastIndexOf("/") + 1))
    }

    DropdownSelection(
        label = stringResource(Res.string.path),
        placeholder = "/",
        selectIndex = selectIndex,
        headItem = {
            DropdownMenuItem(
                text = { Text("Go up") },
                onClick = {
                    if (!loadingPathList && path.isNotEmpty()) {
                        val tempPath = if (parentPath.contains("/") && path != "/") {
                            path.substring(0, path.lastIndexOf("/"))
                        } else {
                            ""
                        }

                        remote?.let {rcloneRemote ->
                            loadingPathList = true
                            scope.launch {
                                onSelectPath?.invoke(rcloneRemote, tempPath)?.let {result ->
                                    result.onSuccess {
                                        it.apply {
                                            pathList.clear()
                                            pathList.addAll(it.filter {
                                                filter?.invoke("${if (tempPath.isBlank()) "" else "$tempPath/"}${it.fileName}")
                                                    ?: true
                                            }.map { networkFile ->
                                                networkFile.fileName
                                            })
                                            path = tempPath
                                            parentPath = tempPath
                                            selectIndex = -1
                                        }
                                    }

                                    result.onFailure {
                                        ToastUtil.error("Error: ${it.message}")
                                        pathList.clear()
                                        pathList.add("/")
                                        parentPath = ""
                                        selectIndex = -1
                                    }
                                }
                                loadingPathList = false
                            }
                        }

                    }
                },

                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        "Go up"
                    )
                },

                trailingIcon = {
                    if (loadingPathList) {
                        Box {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(5.dp)
                                    .align(Alignment.Center)
                                    .size(Dimen.spaceL),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                }
            )

        },
        supportingText = {
            if (path.isNotBlank()) {
                Text("Selected Path: $path")
            }else {
                Text("Selected Path: /")
            }
        },
        list = pathList.toList(),
        onSelect = {
            path =
                if (parentPath.isBlank() || parentPath == "/") pathList[it] else "$parentPath/${pathList[it]}"
            remote?.let { rcloneRemote ->
                scope.launch {
                    onSelectPath?.invoke(rcloneRemote, path)
                }
            }

        },
        onSelectNext = {
            if (!loadingPathList) {
                val tempPath =
                    if (parentPath.isBlank() || parentPath == "/") pathList[it] else "$parentPath/${pathList[it]}"
                remote?.let { rcloneRemote ->
                    loadingPathList = true
                    scope.launch {
                        onSelectPath?.invoke(rcloneRemote, tempPath)?.let {result ->
                            result.onSuccess {
                                if (it.isNotEmpty()) {
                                    it.apply {
                                        if (pathList.isNotEmpty()) {
                                            pathList.removeAll { true }
                                            pathList.addAll(it.filter {
                                                filter?.invoke("${if (tempPath.isBlank()) "" else "$tempPath/"}${it.fileName}")
                                                    ?: true
                                            }.map { networkFile -> networkFile.fileName }
                                                .toList())
                                        }

                                        path = tempPath
                                        parentPath = tempPath
                                        selectIndex =
                                            pathList.indexOf(path.substring(path.lastIndexOf("/") + 1))
                                    }
                                }
                            }

                            result.onFailure {
                                ToastUtil.error("Error: ${it.message}")
                            }
                        }
                        loadingPathList = false
                    }
                }
            }
        }
    )
}