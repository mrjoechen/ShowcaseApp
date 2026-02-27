package com.alpha.showcase.common.ui.source

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.networkfile.storage.COLOR_ICON_STORAGE
import com.alpha.showcase.common.networkfile.storage.DROP_BOX
import com.alpha.showcase.common.networkfile.storage.FTP
import com.alpha.showcase.common.networkfile.storage.GOOGLE_DRIVE
import com.alpha.showcase.common.networkfile.storage.GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.LOCAL
import com.alpha.showcase.common.networkfile.storage.ONE_DRIVE
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.ALBUM
import com.alpha.showcase.common.networkfile.storage.remote.GITEE
import com.alpha.showcase.common.networkfile.storage.remote.GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.TMDB
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.theme.DELETE_COLOR
import com.alpha.showcase.common.ui.dialog.AddLocalSource
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.dialog.DeleteDialog
import com.alpha.showcase.common.ui.dialog.SourceTypeDialog
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.getIcon
import getPlatform
import getPlatformName
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import Screen
import androidx.navigation.NavController
import com.alpha.showcase.common.utils.encodeBase64UrlSafe
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.addSource
import showcaseapp.composeapp.generated.resources.delete
import showcaseapp.composeapp.generated.resources.edit
import showcaseapp.composeapp.generated.resources.select_folder


@Composable
fun SourceListView(
    navController: NavController,
    viewModel: SourceViewModel = SourceViewModel,
    settingViewModel: SettingsViewModel = SettingsViewModel(),
    firstOpen: Boolean = false,
    onClick: (RemoteApi) -> Unit = {}
) {

    var uiState: UiState<StorageSources> by remember {
        mutableStateOf(UiState.Loading)
    }

    var resumed by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(viewModel) {
        viewModel.sourceListStateFlow.collect {
            uiState = it
        }
    }

    uiState.let {
        when (it) {
            is UiState.Error -> DataNotFoundAnim()
            is UiState.Loading -> CircleLoadingIndicator()
            is UiState.Content -> {
                val sources = it.data.sources.toList()
                SourceGrid(sources = sources, viewModel, navController, onClick)
            }
        }
    }

}

@Composable
private fun SourceGrid(
    sources: List<RemoteApi>,
    viewModel: SourceViewModel,
    navController: NavController,
    onClick: ((RemoteApi) -> Unit)? = null
) {
    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var showLocalAddDialog by remember {
        mutableStateOf(false)
    }

    var showOperationDialog by remember {
        mutableStateOf<Operation?>(null)
    }

    var showOperationTargetSource by remember {
        mutableStateOf<RemoteApi?>(null)
    }

    var showConfigDialog by remember {
        mutableStateOf<Int?>(null)
    }

    val scope = rememberCoroutineScope()

    val listState = rememberLazyGridState()
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                showOperationTargetSource = null
            }
    }


    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val vertical = maxHeight > maxWidth * 1.5f
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Adaptive(if (vertical) Dimen.imageSizeVertical else Dimen.imageSizeHorizontal),
            contentPadding = PaddingValues(Dimen.screenContentPadding, 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        showOperationTargetSource = null
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, _ ->
                        showOperationTargetSource = null
                    }
                }
        ) {

            items(sources.size + 1) { index ->

                if (index == sources.size) {
                    AddSourceItem(vertical = vertical) {
                        showOperationTargetSource = null
                        showAddDialog = !showAddDialog
                    }
                } else {
                    val source = sources[index]
                    val scaled by derivedStateOf {
                        showOperationTargetSource?.name == source.name
                    }

                    var focused by remember {
                        mutableStateOf(false)
                    }

                    SourceItem(
                        remoteApi = source,
                        showMoreIcon = source.name == showOperationTargetSource?.name,
                        scaled = scaled || focused,
                        vertical,
                        onClick = {
                            onClick?.invoke(source)
                            showOperationTargetSource = null
                        },
                        onLongClick = {
                            showOperationTargetSource = source
                        },
                        onFocusChanged = {
                            focused = it
                        },
                        onMoreIconClick = {
                            when (it) {
                                is Delete -> {
                                    showOperationDialog = it
                                    showOperationTargetSource = source
                                }

                                is Config -> {
                                    showOperationTargetSource = source
                                    showConfigDialog = source.getType()
                                }

                            }
                        })
                }

            }
        }

//        if (showButton) {
//            ExtendedFloatingActionButton(
//                modifier = Modifier.padding(30.dp).size(60.dp).align(Alignment.BottomEnd),
//                containerColor = MaterialTheme.colorScheme.primary,
//                onClick = {
//                    showAddDialog = !showAddDialog
//                }
//            ) {
//                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.addSource))
//            }
//        }
    }

    if (showAddDialog) {
        SourceTypeDialog {
            showAddDialog = false
            it?.apply {
                when (this) {
                    is LOCAL -> {
                        showLocalAddDialog = true
                    }

                    is SMB, FTP, SFTP, WEBDAV, GITHUB, TMDB, GITEE -> {
                        showConfigDialog = this.type
                    }

                    is GOOGLE_DRIVE, GOOGLE_PHOTOS, ONE_DRIVE, DROP_BOX -> {
                        showConfigDialog = this.type
                    }

                    UNSPLASH, PEXELS, ALBUM, IMMICH -> {
                        showConfigDialog = this.type
                    }

                    else -> {
                    }
                }

            }
        }
    }

    if (showOperationDialog == Delete) {
        showOperationTargetSource?.apply {
            DeleteDialog(
                deleteName = name.decodeName(),
                onConfirm = {
                    scope.launch {
                        viewModel.deleteSource(this@apply)
                        showOperationTargetSource = null
                        showOperationDialog = null
                    }
                },
                onCancel = {
                    showOperationTargetSource = null
                    showOperationDialog = null
                },
                onDismiss = {
                    showOperationTargetSource = null
                    showOperationDialog = null
                })
        }
    }

    showConfigDialog?.let { type ->
        val sourceNameArg = showOperationTargetSource?.name?.encodeBase64UrlSafe().orEmpty()
        val route = if (sourceNameArg.isNotBlank()) {
            "${Screen.Config.route}/$type?sourceName=$sourceNameArg"
        } else {
            "${Screen.Config.route}/$type"
        }
        LaunchedEffect(route) {
            navController.navigate(route)
            showConfigDialog = null
            showOperationTargetSource = null
        }
    }


    if (showLocalAddDialog) {
        var name by remember {
            mutableStateOf("")
        }

        val fileLauncher = rememberDirectoryPickerLauncher(
            title = stringResource(Res.string.select_folder)
        ) { directory ->
            if (directory != null) {
                scope.launch {
                    viewModel.addSourceList(Local(name = name, path = directory.path, platform = getPlatform().platform.platformName))
                    showLocalAddDialog = false
                }

            }
        }

        AddLocalSource(
            onCancelClick = {
                showLocalAddDialog = false
            },
            onConfirmClick = {
                name = it
                scope.launch {
                    fileLauncher.launch()
                }

            }
        )

    }
}


private val MORE_OPERATION by lazy { listOf(Config, Delete) }

@Composable
private fun SourceItem(
    remoteApi: RemoteApi,
    showMoreIcon: Boolean = false,
    scaled: Boolean = false,
    vertical: Boolean,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreIconClick: (Operation) -> Unit = {}
) {

    val moreChoice = remember {
        mutableStateOf(if (remoteApi is Local) listOf(Delete) else MORE_OPERATION)
    }

    Column {
        SourceItemBackground(
            vertical = vertical,
            scaled = scaled,
            onFocusChanged = {
                onFocusChanged?.invoke(it)
            },
            onClick = {
                onClick.invoke()
            },
            onLongClick = {
                onLongClick.invoke()
            }) {

            Box {
                Icon(
                    painter = painterResource(remoteApi.getIcon()),
                    contentDescription = remoteApi.name,
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    tint = if (remoteApi.getIcon() in COLOR_ICON_STORAGE) Color.Unspecified else LocalContentColor.current
                )

                if (showMoreIcon) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DELETE_COLOR)
                            .wrapContentHeight()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {

                        moreChoice.value.forEach {
                            when (it) {
                                is Config -> {
                                    Icon(
                                        Icons.Outlined.EditNote,
                                        contentDescription = stringResource(Res.string.edit),
                                        tint = MaterialTheme.colorScheme.background,
                                        modifier = Modifier
                                            .clickable {
                                                onMoreIconClick.invoke(Config)
                                            }
                                            .padding(3.dp)
                                            .weight(1f)
                                    )
                                }

                                is Delete -> {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(Res.string.delete),
                                        tint = MaterialTheme.colorScheme.background,
                                        modifier = Modifier
                                            .clickable {
                                                onMoreIconClick.invoke(Delete)
                                            }
                                            .padding(3.dp)
                                            .weight(1f)
                                    )
                                }

                            }
                        }
                    }
                }
            }


        }
        Text(
            text = remoteApi.name.decodeName(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(5.dp)
                .align(Alignment.CenterHorizontally).basicMarquee()
        )
    }

}

@Composable
private fun AddSourceItem(vertical: Boolean, onClick: () -> Unit) {
    var scaled by remember {
        mutableStateOf(false)
    }
    Column {
        SourceItemBackground(
            vertical = vertical,
            scaled = scaled,
            onFocusChanged = {
                scaled = it
            },
            onClick = {
                onClick.invoke()
                scaled = false
            }) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(Res.string.addSource),
                modifier = Modifier
                    .padding(Dimen.spaceXL)
                    .fillMaxSize()
            )
        }
        Text(
            text = stringResource(Res.string.addSource),
            fontSize = 14.sp,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(5.dp)
                .align(Alignment.CenterHorizontally)
        )
    }

}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun SourceItemBackground(
    vertical: Boolean,
    scaled: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    val scale = animateFloatAsState(if (scaled) 1.05f else 1f)
    val pressedInteractionSource = remember { MutableInteractionSource() }


    ElevatedCard(
        modifier = Modifier
            .padding(Dimen.spaceM)
            .fillMaxWidth()
            .onFocusChanged {
                onFocusChanged?.invoke(it.isFocused)
            }
            .scale(scale.value)
            .height((if (vertical) Dimen.imageSizeVertical else Dimen.imageSizeHorizontal) * 3f / 2)
            .shadow(2.dp, CardDefaults.elevatedShape)
//      .onClick(
//        matcher = PointerMatcher.mouse(PointerButton.Secondary), // Right Mouse Button
//        onLongClick = {
//          onLongClick?.invoke()
//        },
//        onClick = {
//          onLongClick?.invoke()
//        }
//      )
            .combinedClickable(
                interactionSource = pressedInteractionSource,
                indication = LocalIndication.current,
                onClick = onClick ?: {},
                onDoubleClick = {
                    onLongClick?.invoke()
                },
                onLongClick = {
                    onLongClick?.invoke()
                })
            .pointerInput(Unit) {
                awaitPointerEventScope{
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        when (event.type) {
                            PointerEventType.Enter -> {
                                onFocusChanged?.invoke(true)
                            }

                            PointerEventType.Exit -> {
                                onFocusChanged?.invoke(false)
                            }

                            PointerEventType.Press -> {
                                if (event.buttons.isSecondaryPressed) {
                                    onLongClick?.invoke()
                                }
                            }
                        }
                    }
                }
            }
//      .onPointerEvent(PointerEventType.Enter) {
//        onFocusChanged?.invoke(true)
//      }.onPointerEvent(PointerEventType.Exit) {
//        onFocusChanged?.invoke(false)
//      }
        ,
//    onClick = onClick?: {},
        content = content
    )
}
