package com.alpha.showcase.common.ui.source

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.networkfile.storage.DROP_BOX
import com.alpha.networkfile.storage.FTP
import com.alpha.networkfile.storage.GOOGLE_DRIVE
import com.alpha.networkfile.storage.GOOGLE_PHOTOS
import com.alpha.networkfile.storage.LOCAL
import com.alpha.networkfile.storage.ONE_DRIVE
import com.alpha.networkfile.storage.SFTP
import com.alpha.networkfile.storage.SMB
import com.alpha.networkfile.storage.StorageSources
import com.alpha.networkfile.storage.WEBDAV
import com.alpha.networkfile.storage.external.GITHUB
import com.alpha.networkfile.storage.external.TMDB
import com.alpha.networkfile.storage.external.UNSPLASH
import com.alpha.networkfile.storage.remote.Local
import com.alpha.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.DELETE_COLOR
import com.alpha.showcase.common.ui.theme.Dimen
import com.alpha.showcase.common.ui.dialog.COLOR_ICON_STORAGE
import com.alpha.showcase.common.ui.dialog.DeleteDialog
import com.alpha.showcase.common.ui.dialog.SourceTypeDialog
import com.alpha.showcase.common.ui.settings.ProgressIndicator
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.getIcon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource


@Composable
fun SourceListView(
  viewModel: SourceViewModel = SourceViewModel,
  settingViewModel: SettingsViewModel = SettingsViewModel(),
  firstOpen: Boolean = false
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
      is UiState.Loading -> ProgressIndicator()
      is UiState.Content -> {
        val sources = it.data.sources.toList()
        SourceGrid(sources = sources, viewModel)
      }
    }
  }

}

@Composable
private fun SourceGrid(sources: List<RemoteApi<Any>>, viewModel: SourceViewModel) {
  var showAddDialog by remember {
    mutableStateOf(false)
  }

  val vertical by remember {
    mutableStateOf(false)
  }

  var showLocalAddDialog by remember {
    mutableStateOf(false)
  }

  var showOperationDialog by remember {
    mutableStateOf<Operation?>(null)
  }

  var showOperationTargetSource by remember {
    mutableStateOf<RemoteApi<Any>?>(null)
  }

  val scope = rememberCoroutineScope()

  val listState = rememberLazyGridState()
  val showButton by remember {
    derivedStateOf {
      listState.firstVisibleItemIndex > 0
    }
  }

  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
      .collect { index ->
        showOperationTargetSource = null
      }
  }

  LazyVerticalGrid(
    state = listState,
    columns = GridCells.Adaptive(if (vertical) Dimen.imageSizeVertical else Dimen.imageSizeHorizontal),
    contentPadding = PaddingValues(Dimen.screenContentPadding),
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTapGestures {
          showOperationTargetSource = null
        }
        detectDragGestures { _, _ ->
          showOperationTargetSource = null
        }
      }
  ) {

    items(sources.size + 1 ) { index ->

     if (index == sources.size) {
        AddSourceItem(vertical = vertical) {
          showAddDialog = !showAddDialog
        }
      } else {
        val source = sources[index]
        val scaled = derivedStateOf {
          showOperationTargetSource?.name == source.name
        }

        var focused by remember {
          mutableStateOf(false)
        }
        SourceItem(
          remoteApi = source,
          showMoreIcon = source.name == showOperationTargetSource?.name,
          scaled = scaled.value || focused,
          vertical,
          onClick = {
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
                showOperationTargetSource = null
              }

              else -> {
                // do nothing
              }
            }
          })
      }

    }
  }

  if (showAddDialog) {
    SourceTypeDialog {
      showAddDialog = false
      it?.apply {
        when (this) {
          is LOCAL -> {
            showLocalAddDialog = true
          }

          is SMB, FTP, SFTP, WEBDAV, GITHUB, TMDB -> {

          }

          is GOOGLE_DRIVE, GOOGLE_PHOTOS, ONE_DRIVE, DROP_BOX -> {

          }

          UNSPLASH -> {
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


  if (showLocalAddDialog) {
    var name by remember {
      mutableStateOf("")
    }

  }
}


private val MORE_OPERATION by lazy { listOf(Config, Delete) }

@Composable
private fun SourceItem(
  remoteApi: RemoteApi<Any>,
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
                    contentDescription = "Edit Source",
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
                    contentDescription = "Delete Source",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                      .clickable {
                        onMoreIconClick.invoke(Delete)
                      }
                      .padding(3.dp)
                      .weight(1f)
                  )
                }

                else -> {

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
        .align(Alignment.CenterHorizontally)
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
      }) {
      Icon(
        Icons.Outlined.Add,
        contentDescription = "Add Source",
        modifier = androidx.compose.ui.Modifier
          .padding(Dimen.spaceXL)
          .fillMaxSize()
      )
    }
    Text(
      text = "Add Source",
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

  val scale = animateFloatAsState(if (scaled) 1.1f else 1f)
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
      .shadow(1.dp, CardDefaults.elevatedShape)
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
        indication = rememberRipple(),
        onClick = onClick ?: {},
        onLongClick = {
          onLongClick?.invoke()
        })
//      .onPointerEvent(PointerEventType.Enter) {
//        onFocusChanged?.invoke(true)
//      }.onPointerEvent(PointerEventType.Exit) {
//        onFocusChanged?.invoke(false)
//      }
    ,
    elevation = CardDefaults.elevatedCardElevation(2.dp, 4.dp, 5.dp, 5.dp, 6.dp, 2.dp),
//    onClick = onClick?: {},
    content = content
  )
}