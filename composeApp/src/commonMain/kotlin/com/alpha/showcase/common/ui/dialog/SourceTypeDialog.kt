package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.networkfile.storage.ALIST
import com.alpha.showcase.common.networkfile.storage.DROP_BOX
import com.alpha.showcase.common.networkfile.storage.FTP
import com.alpha.showcase.common.networkfile.storage.GOOGLE_DRIVE
import com.alpha.showcase.common.networkfile.storage.GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.LOCAL
import com.alpha.showcase.common.networkfile.storage.ONE_DRIVE
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.StorageType
import com.alpha.showcase.common.networkfile.storage.UNION
import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.storage.remote.GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.TMDB
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.theme.Dimen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_dropbox
import showcaseapp.composeapp.generated.resources.ic_ftp
import showcaseapp.composeapp.generated.resources.ic_github
import showcaseapp.composeapp.generated.resources.ic_google_drive
import showcaseapp.composeapp.generated.resources.ic_google_photos
import showcaseapp.composeapp.generated.resources.ic_local
import showcaseapp.composeapp.generated.resources.ic_onedrive
import showcaseapp.composeapp.generated.resources.ic_pexels
import showcaseapp.composeapp.generated.resources.ic_smb
import showcaseapp.composeapp.generated.resources.ic_terminal
import showcaseapp.composeapp.generated.resources.ic_tmdb
import showcaseapp.composeapp.generated.resources.ic_union
import showcaseapp.composeapp.generated.resources.ic_unsplash_symbol
import showcaseapp.composeapp.generated.resources.ic_webdav
import showcaseapp.composeapp.generated.resources.ic_alist

val SUPPORT_LIST = listOf(
  LOCAL to Res.drawable.ic_local,
  SMB to Res.drawable.ic_smb,
  FTP to Res.drawable.ic_ftp,
  SFTP to Res.drawable.ic_terminal,
  WEBDAV to Res.drawable.ic_webdav,
  TMDB to Res.drawable.ic_tmdb,
  GITHUB to Res.drawable.ic_github,
  GOOGLE_PHOTOS to Res.drawable.ic_google_photos,
  GOOGLE_DRIVE to Res.drawable.ic_google_drive,
  ONE_DRIVE to Res.drawable.ic_onedrive,
  DROP_BOX to Res.drawable.ic_dropbox,
  UNION to Res.drawable.ic_union,
  UNSPLASH to Res.drawable.ic_unsplash_symbol,
  PEXELS to Res.drawable.ic_pexels,
  ALIST to Res.drawable.ic_alist
)

val COLOR_ICON_STORAGE = listOf(
  Res.drawable.ic_google_drive,
  Res.drawable.ic_onedrive,
  Res.drawable.ic_google_photos,
  Res.drawable.ic_dropbox,
  Res.drawable.ic_tmdb,
  Res.drawable.ic_alist
)

@Composable
fun SourceTypeDialog(onTypeClick: (StorageType?) -> Unit = {}) {

  Dialog(
    properties = DialogProperties(usePlatformDefaultWidth = false),
    onDismissRequest = {
      onTypeClick(null)
    }
  ) {
    Surface(
      modifier = Modifier
        .padding(Dimen.spaceL)
        .wrapContentSize(),
      shape = MaterialTheme.shapes.medium,
      tonalElevation = 5.dp,
      shadowElevation = 9.dp
    ) {

      LazyVerticalGrid(
        modifier = Modifier
          .sizeIn(maxWidth = 400.dp, maxHeight = 300.dp),
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(Dimen.spaceM)
      ) {

        items(SUPPORT_LIST.size) {
          val res = SUPPORT_LIST[it]
          Item(res) {
            onTypeClick(res.first)
          }
        }
      }
    }
  }
}


@Composable
fun Item(res: Pair<StorageType, DrawableResource>, onClick: () -> Unit = {}) {

  Surface(shape = RoundedCornerShape(5.dp), color = Color.Transparent, onClick = {
    onClick()
  }) {
    Column(
      modifier = Modifier
        .padding(Dimen.spaceM),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      Icon(
        painter = painterResource(res.second),
        contentDescription = res.first.typeName,
        tint = if (res.second in COLOR_ICON_STORAGE) Color.Unspecified else LocalContentColor.current
      )
      Text(
        text = res.first.typeName,
        style = MaterialTheme.typography.bodySmall.merge(), modifier = Modifier.padding(Dimen.spaceM),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Preview
@Composable
fun PreviewItem() {
  Column {
    SUPPORT_LIST.forEach {
      Item(res = it)
    }

  }
}