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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.networkfile.storage.COLOR_ICON_STORAGE
import com.alpha.showcase.common.networkfile.storage.SUPPORT_LIST
import com.alpha.showcase.common.networkfile.storage.StorageType
import com.alpha.showcase.common.networkfile.storage.getCurrentPlatformSupportTypes
import com.alpha.showcase.common.theme.Dimen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview


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

      val items by remember {
        mutableStateOf(getCurrentPlatformSupportTypes())
      }
      LazyVerticalGrid(
        modifier = Modifier
          .sizeIn(maxWidth = 400.dp, maxHeight = 500.dp, minHeight = 200.dp),
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(Dimen.spaceXL)
      ) {

        items(items.size) {
          val res = items[it]
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