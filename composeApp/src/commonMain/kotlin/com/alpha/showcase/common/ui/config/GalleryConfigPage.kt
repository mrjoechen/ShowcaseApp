package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.showcase.common.cache.GalleryMediaRecord
import com.alpha.showcase.common.cache.GallerySourceMediaStore
import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.source.toGalleryDisplayUri
import com.alpha.showcase.common.ui.source.toGalleryMediaInput
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.utils.ToastUtil
import ensureGalleryReadPermissionIfNeeded
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import persistGalleryUriPermission
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.add
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.delete
import showcaseapp.composeapp.generated.resources.gallery_add_photos
import showcaseapp.composeapp.generated.resources.gallery_no_new_photos
import showcaseapp.composeapp.generated.resources.gallery_no_photos
import showcaseapp.composeapp.generated.resources.gallery_selected_photos
import showcaseapp.composeapp.generated.resources.gallery_source_not_found
import showcaseapp.composeapp.generated.resources.gallery_updated
import showcaseapp.composeapp.generated.resources.no_photo_selected

private enum class GalleryLayoutMode {
    LIST,
    GRID,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryConfigPage(
    gallerySource: GallerySource?
) {
    if (gallerySource == null) {
        DataNotFoundAnim(stringResource(Res.string.gallery_source_not_found))
        return
    }

    val mediaStore = remember { GallerySourceMediaStore() }
    val scope = rememberCoroutineScope()
    val sourceName = gallerySource.name

    var loading by remember(sourceName) { mutableStateOf(true) }
    var mediaItems by remember(sourceName) { mutableStateOf<List<GalleryMediaRecord>>(emptyList()) }
    var layoutMode by remember(sourceName) { mutableStateOf(GalleryLayoutMode.LIST) }
    var selectedUris by remember(sourceName) { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectedDialog by remember(sourceName) { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            mediaItems = runCatching {
                mediaStore.listMedias(sourceName)
            }
                .getOrElse {
                    it.printStackTrace()
                    ToastUtil.error(it.message ?: "Failed to load media")
                    emptyList()
                }
            loading = false
        }
    }

    fun toggleSelected(mediaUri: String) {
        selectedUris = if (mediaUri in selectedUris) {
            selectedUris - mediaUri
        } else {
            selectedUris + mediaUri
        }
    }

    val pickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Multiple(),
        title = stringResource(Res.string.gallery_add_photos),
    ) { selectedFiles ->
        if (selectedFiles.isNullOrEmpty()) {
            ToastUtil.toast(Res.string.no_photo_selected)
            return@rememberFilePickerLauncher
        }
        scope.launch {
            val medias = withContext(Dispatchers.Default) {
                selectedFiles.mapNotNull { file ->
                    file.toGalleryMediaInput(sourceName)?.also {
                        persistGalleryUriPermission(it.mediaUri)
                    }
                }
            }
            if (medias.isEmpty()) {
                ToastUtil.error(Res.string.no_photo_selected)
                return@launch
            }
            val inserted = runCatching {
                mediaStore.addMedias(sourceName, medias)
            }.getOrElse {
                it.printStackTrace()
                ToastUtil.error(it.message ?: "Failed to update gallery")
                0
            }

            if (inserted > 0) {
                ToastUtil.success(Res.string.gallery_updated)
            } else {
                ToastUtil.toast(Res.string.gallery_no_new_photos)
            }
            reload()
        }
    }

    LaunchedEffect(sourceName) {
        reload()
    }

    LaunchedEffect(layoutMode) {
        if (layoutMode == GalleryLayoutMode.LIST && selectedUris.isNotEmpty()) {
            selectedUris = emptySet()
        }
    }

    LaunchedEffect(mediaItems) {
        if (selectedUris.isNotEmpty()) {
            val validUris = mediaItems.map { it.mediaUri }.toSet()
            selectedUris = selectedUris.intersect(validUris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val selectingInGrid = layoutMode == GalleryLayoutMode.GRID && selectedUris.isNotEmpty()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${stringResource(Res.string.gallery_selected_photos)} (${mediaItems.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectingInGrid) {
                    Text(
                        text = "已选择 ${selectedUris.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        layoutMode = if (layoutMode == GalleryLayoutMode.LIST) {
                            GalleryLayoutMode.GRID
                        } else {
                            GalleryLayoutMode.LIST
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (layoutMode == GalleryLayoutMode.LIST) {
                            Icons.Outlined.GridOn
                        } else {
                            Icons.AutoMirrored.Outlined.FormatListBulleted
                        },
                        contentDescription = if (layoutMode == GalleryLayoutMode.LIST) {
                            "Switch to grid"
                        } else {
                            "Switch to list"
                        }
                    )
                }

                ElevatedButton(
                    onClick = {
                        if (selectingInGrid) {
                            showDeleteSelectedDialog = true
                            return@ElevatedButton
                        }
                        ensureGalleryReadPermissionIfNeeded()
                        pickerLauncher.launch()
                    }
                ) {
                    Icon(
                        imageVector = if (selectingInGrid) {
                            Icons.Outlined.DeleteOutline
                        } else {
                            Icons.Outlined.Add
                        },
                        contentDescription = if (selectingInGrid) {
                            stringResource(Res.string.delete)
                        } else {
                            stringResource(Res.string.add)
                        }
                    )
                    Spacer(modifier = Modifier.size(Dimen.spaceS))
                    Text(
                        text = if (selectingInGrid) {
                            "${stringResource(Res.string.delete)} (${selectedUris.size})"
                        } else {
                            stringResource(Res.string.gallery_add_photos)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (loading) {
            CircleLoadingIndicator()
            return@Column
        }

        if (mediaItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DataNotFoundAnim(stringResource(Res.string.gallery_no_photos))
            }
            return@Column
        }

        if (layoutMode == GalleryLayoutMode.LIST) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mediaItems.size) { index ->
                    val media = mediaItems[index]
                    val readableName = resolveDisplayName(media)
                    Surface(
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = toGalleryDisplayUri(media.mediaUri),
                                contentDescription = readableName,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = readableName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            mediaStore.deleteMedias(sourceName, listOf(media.mediaUri))
                                        }.onSuccess {
                                            mediaItems = mediaItems.filterNot { it.mediaUri == media.mediaUri }
                                        }.onFailure {
                                            it.printStackTrace()
                                            ToastUtil.error(it.message ?: "Failed to remove media")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(Res.string.delete)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedUris, layoutMode) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                if (layoutMode == GalleryLayoutMode.GRID && selectedUris.isNotEmpty()) {
                                    val hitItem = gridState.layoutInfo.visibleItemsInfo.any { item ->
                                        val left = item.offset.x.toFloat()
                                        val top = item.offset.y.toFloat()
                                        val right = left + item.size.width
                                        val bottom = top + item.size.height
                                        tapOffset.x in left..right && tapOffset.y in top..bottom
                                    }
                                    if (!hitItem) {
                                        selectedUris = emptySet()
                                    }
                                }
                            }
                        )
                    },
                state = gridState,
                columns = GridCells.Adaptive(minSize = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(mediaItems.size) { index ->
                    val media = mediaItems[index]
                    val readableName = resolveDisplayName(media)
                    val isSelected = media.mediaUri in selectedUris

                    Surface(
                        tonalElevation = if (isSelected) 4.dp else 2.dp,
                        shadowElevation = 2.dp,
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedUris.isNotEmpty()) {
                                            toggleSelected(media.mediaUri)
                                        }
                                    },
                                    onLongClick = {
                                        toggleSelected(media.mediaUri)
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = toGalleryDisplayUri(media.mediaUri),
                                contentDescription = readableName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteSelectedDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteSelectedDialog = false
                },
                title = {
                    Text(text = stringResource(Res.string.delete))
                },
                text = {
                    Text(
                        text = "将从列表中移除 ${selectedUris.size} 张照片，不会删除系统相册中的原文件。"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetUris = selectedUris
                            showDeleteSelectedDialog = false
                            if (targetUris.isEmpty()) return@TextButton
                            scope.launch {
                                runCatching {
                                    mediaStore.deleteMedias(sourceName, targetUris.toList())
                                }.onSuccess {
                                    mediaItems = mediaItems.filterNot { it.mediaUri in targetUris }
                                    selectedUris = emptySet()
                                }.onFailure {
                                    it.printStackTrace()
                                    ToastUtil.error(it.message ?: "Failed to remove selected media")
                                }
                            }
                        }
                    ) {
                        Text(text = stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteSelectedDialog = false
                        }
                    ) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                }
            )
        }
    }
}

private fun resolveDisplayName(media: GalleryMediaRecord): String {
    val directName = media.displayName.trim()
    if (directName.isNotBlank() && !directName.contains("://")) {
        return directName
    }

    val fallback = if (directName.isNotBlank()) directName else media.mediaUri
    return fallback
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .ifBlank { media.mediaUri.substringAfterLast('/').ifBlank { media.mediaUri } }
}
