package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.networkfile.storage.COLOR_ICON_STORAGE
import com.alpha.showcase.common.networkfile.storage.FTP
import com.alpha.showcase.common.networkfile.storage.LOCAL
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.SUPPORT_LIST
import com.alpha.showcase.common.networkfile.storage.StorageType
import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.storage.getCurrentPlatformSupportTypes
import com.alpha.showcase.common.networkfile.storage.remote.ALBUM
import com.alpha.showcase.common.networkfile.storage.remote.GALLERY
import com.alpha.showcase.common.networkfile.storage.remote.GITEE
import com.alpha.showcase.common.networkfile.storage.remote.GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.RSS
import com.alpha.showcase.common.networkfile.storage.remote.S3
import com.alpha.showcase.common.networkfile.storage.remote.TMDB
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.theme.Dimen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.source_type_category_album_services
import showcaseapp.composeapp.generated.resources.source_type_category_local
import showcaseapp.composeapp.generated.resources.source_type_category_network_storage
import showcaseapp.composeapp.generated.resources.source_type_category_other_online_content
import showcaseapp.composeapp.generated.resources.source_type_category_third_party

internal data class SourceTypeSection(
  val titleRes: StringResource,
  val options: List<Pair<StorageType, DrawableResource>>,
)

internal fun buildSourceTypeSections(
  supportedTypes: List<Pair<StorageType, DrawableResource>>,
): List<SourceTypeSection> {
  val optionsByType = supportedTypes.associateBy { it.first }
  val categoryDefinitions = listOf(
    Res.string.source_type_category_local to listOf(GALLERY, LOCAL),
    Res.string.source_type_category_network_storage to listOf(SMB, FTP, SFTP, WEBDAV, S3),
    Res.string.source_type_category_third_party to listOf(TMDB, UNSPLASH, PEXELS, GITHUB, GITEE),
    Res.string.source_type_category_album_services to listOf(IMMICH, MTPHOTO),
    Res.string.source_type_category_other_online_content to listOf(ALBUM, RSS),
  )
  val categorizedTypes = categoryDefinitions.flatMap { it.second }.toSet()
  val uncategorizedOptions = supportedTypes.filterNot { it.first in categorizedTypes }

  return categoryDefinitions.mapIndexedNotNull { index, (titleRes, types) ->
    val fallbackOptions = if (index == categoryDefinitions.lastIndex) {
      uncategorizedOptions
    } else {
      emptyList()
    }
    val options = types.mapNotNull(optionsByType::get) + fallbackOptions
    options.takeIf { it.isNotEmpty() }?.let { SourceTypeSection(titleRes, it) }
  }
}

@Composable
fun SourceTypeDialog(onTypeClick: (StorageType?) -> Unit = {}) {
  val sections = remember {
    buildSourceTypeSections(getCurrentPlatformSupportTypes())
  }

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
      shape = MaterialTheme.shapes.large,
      tonalElevation = 5.dp,
      shadowElevation = 9.dp
    ) {

      LazyVerticalGrid(
        modifier = Modifier
          .sizeIn(maxWidth = 400.dp, maxHeight = 520.dp),
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(Dimen.spaceM)
      ) {
        sections.forEachIndexed { index, section ->
          item(
            key = "source_type_section_$index",
            span = { GridItemSpan(maxLineSpan) },
          ) {
            SourceTypeSectionHeader(stringResource(section.titleRes))
          }
          items(
            items = section.options,
            key = { (type) -> type.type },
          ) { option ->
            Item(option) {
              onTypeClick(option.first)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SourceTypeSectionHeader(title: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Dimen.spaceL),
  ) {
    Text(
      text = title,
      modifier = Modifier.semantics { heading() },
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun Item(res: Pair<StorageType, DrawableResource>, onClick: () -> Unit = {}) {

  Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent, onClick = {
    onClick()
  }) {
    Column(
      modifier = Modifier
        .padding(Dimen.spaceM),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      Icon(
        painter = painterResource(res.second),
        contentDescription = res.first.displayName,
        modifier = Modifier.size(48.dp),
        tint = if (res.second in COLOR_ICON_STORAGE) Color.Unspecified else LocalContentColor.current
      )
      Text(
        text = res.first.displayName,
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
    buildSourceTypeSections(SUPPORT_LIST).flatMap { it.options }.forEach {
      Item(res = it)
    }
  }
}
