package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.decodeUrlPath
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.choose_folder
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.error
import showcaseapp.composeapp.generated.resources.loading
import showcaseapp.composeapp.generated.resources.retry


@Composable
fun NetworkFolderPickerDialog(
    showDialog: Boolean,
    remoteApi: RcloneRemoteApi,
    initialPath: String = "/",
    onDismiss: () -> Unit,
    onPathSelected: (String) -> Unit
) {
    if (showDialog) {
        val viewModel = remember { NetworkFolderPickerViewModel() }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        LaunchedEffect(remoteApi, initialPath) {
            viewModel.initialize(remoteApi, initialPath)
        }

        Dialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss
        ) {
            Surface(
                modifier = Modifier.padding(Dimen.spaceL)
                    .widthIn(400.dp, 600.dp)
                    .height(500.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 5.dp,
                shadowElevation = 9.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.choose_folder),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        var showOptionsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "Options")
                            }
                            
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    onClick = {
                                        viewModel.setDisplayMode(DisplayMode.FOLDERS_ONLY)
                                        showOptionsMenu = false
                                    },
                                    text = { Text("Folders Only") }
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        viewModel.setDisplayMode(DisplayMode.FOLDERS_AND_FILES)
                                        showOptionsMenu = false
                                    },
                                    text = { Text("Folders and Files") }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 面包屑路径导航
                    PathBreadcrumb(
                        currentPath = uiState.currentPath,
                        onPathClick = { path ->
                            scope.launch {
                                viewModel.navigateToPath(path)
                            }
                        },
                        onUpClick = {
                            scope.launch {
                                viewModel.navigateUp()
                            }
                        },
                        isLoading = uiState.isLoading
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 显示模式切换
//                    Row(
//                        modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth(),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text(
//                            text = "显示模式：${if (uiState.displayMode == DisplayMode.FOLDERS_ONLY) "仅文件夹" else "文件夹和文件"}",
//                            style = MaterialTheme.typography.bodySmall
//                        )
//                        Spacer(modifier = Modifier.weight(1f))
//                        Switch(
//                            checked = uiState.displayMode == DisplayMode.FOLDERS_AND_FILES,
//                            onCheckedChange = { enabled ->
//                                val mode = if (enabled) DisplayMode.FOLDERS_AND_FILES else DisplayMode.FOLDERS_ONLY
//                                viewModel.setDisplayMode(mode)
//                            }
//                        )
//                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    // 文件列表
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (uiState.isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ){
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(Res.string.loading),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else if (uiState.errorMessage != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    text = "${stringResource(Res.string.error)}:${uiState.errorMessage}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.refreshCurrentPath()
                                        }
                                    }
                                ) {
                                    Text(stringResource(Res.string.retry))
                                }
                            }
                        } else {
                            LazyColumn {
                                items(uiState.items) { item ->
                                    NetworkFolderItem(
                                        item = item,
                                        onClick = {
                                            if (item.isDirectory) {
                                                scope.launch {
                                                    viewModel.navigateToPath(item.path)
                                                }
                                            }
                                        },
                                        onEnterClick = if (item.isDirectory) {
                                            {
                                                scope.launch {
                                                    viewModel.navigateToPath(item.path)
                                                }
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 底部按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.cancel))
                        }
                        
                        TextButton(
                            onClick = {
                                onPathSelected(uiState.currentPath)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                }
            }
        }

        // 处理错误消息
        LaunchedEffect(uiState.errorMessage) {
            uiState.errorMessage?.let { message ->
                ToastUtil.error(message)
            }
        }
    }
}

@Composable
private fun NetworkFolderItem(
    item: NetworkFolderItem,
    onClick: () -> Unit,
    onEnterClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = if (item.isDirectory) "Folder" else "File",
            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!item.isDirectory && item.size > 0) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        
        if (onEnterClick != null) {
            IconButton(onClick = onEnterClick) {
                Icon(
                    Icons.Outlined.ArrowForwardIos,
                    contentDescription = "Enter",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PathBreadcrumb(
    currentPath: String,
    onPathClick: (String) -> Unit,
    onUpClick: () -> Unit,
    isLoading: Boolean
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件夹图标
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = "Current folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 面包屑路径
            val pathSegments = buildPathSegments(currentPath)
            
            // 当路径变化时自动滚动到最右端
            LaunchedEffect(currentPath) {
                scope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pathSegments.forEachIndexed { index, segment ->
                    // 路径分割线（除了第一个元素）
                    if (index > 0) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                    
                    // 路径段
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = !isLoading) {
                            onPathClick(segment.fullPath)
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (index == pathSegments.lastIndex) {
                            // 当前路径高亮
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            // 可点击的父路径
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = segment.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == pathSegments.lastIndex) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // 返回上级按钮
            IconButton(
                onClick = onUpClick,
                enabled = !isLoading && currentPath.isNotEmpty() && currentPath != "/"
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Go up",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

data class PathSegment(
    val name: String,
    val fullPath: String
)

private fun buildPathSegments(currentPath: String): List<PathSegment> {
    if (currentPath.isEmpty() || currentPath == "/") {
        return listOf(PathSegment("根目录", "/"))
    }
    
    val segments = mutableListOf(PathSegment("根目录", "/"))
    val pathParts = currentPath.split("/").filter { it.isNotEmpty() }
    
    var buildPath = ""
    pathParts.forEach { part ->
        buildPath += "/$part"
        val displayName = try { decodeUrlPath(part) } catch (_: Exception) { part }
        segments.add(PathSegment(displayName, buildPath))
    }
    
    return segments
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toLong() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toLong() / 10.0} MB"
    val gb = mb / 1024.0
    return "${(gb * 10).toLong() / 10.0} GB"
}