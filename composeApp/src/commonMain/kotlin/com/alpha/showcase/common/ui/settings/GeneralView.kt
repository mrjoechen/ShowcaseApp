package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.theme.AppThemeStyle
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.SegmentedControl
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.ui.view.TextTitleLarge
import com.alpha.showcase.common.ui.view.TextTitleMedium
import com.alpha.showcase.common.ui.view.rememberMobileHaptic
import getPlatform
import isDesktop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.anonymous_usage
import showcaseapp.composeapp.generated.resources.are_you_clear_cache
import showcaseapp.composeapp.generated.resources.cache_size
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.dark_mode
import showcaseapp.composeapp.generated.resources.language
import showcaseapp.composeapp.generated.resources.showcase_general
import showcaseapp.composeapp.generated.resources.theme_style
import kotlin.math.abs


/**
 *
 * - General
 *      - Language
 *      - Theme
 *      - Anonymous Usage
 */
@Composable
fun GeneralView(
    generalPreference: GeneralPreference,
    onSet: (String, Any) -> Unit
){

  var showClearCacheDialog by remember {
    mutableStateOf(false)
  }

  var showLanguageSelectDialog by remember {
    mutableStateOf(false)
  }
  val scope = rememberCoroutineScope()

  Column {
//    IconItem(Icons.Outlined.Language, desc = stringResource(Res.string.language), onClick = {
//      showLanguageSelectDialog = !showLanguageSelectDialog
//    }){
//      Text(text = stringResource(Res.string.language), color = LocalContentColor.current.copy(0.6f))
//    }

    IconItem(Icons.Outlined.Bedtime, desc = stringResource(Res.string.dark_mode), onClick = {

    }){

      SegmentedControl(
        items = DarkThemePreference.darkThemeChoices(),
        defaultSelectedItemIndex = generalPreference.darkMode,
        contentPadding = PaddingValues(10.dp, 4.dp),
      ) {
        onSet(GeneralPreferenceKey.DarkMode, it)
      }

    }

    IconItem(Icons.Outlined.Palette, desc = stringResource(Res.string.theme_style), onClick = {

    }) {
      Text(
        text = AppThemeStyle.fromValue(generalPreference.themeStyle).label(),
        color = LocalContentColor.current.copy(0.6f)
      )
    }

    ThemeStylePicker(
      currentThemeStyle = AppThemeStyle.fromValue(generalPreference.themeStyle),
      onThemeSelected = {
        onSet(GeneralPreferenceKey.ThemeStyle, it.value)
      }
    )

    SwitchItem(
      Icons.Outlined.Analytics,
      check = generalPreference.anonymousUsage,
      desc = stringResource(Res.string.anonymous_usage),
      onCheck = {
        onSet(GeneralPreferenceKey.AnonymousUsage, it)
      })

    SlideItem(
      Icons.Outlined.DonutLarge,
      desc = stringResource(Res.string.cache_size),
      value = generalPreference.cacheSize,
      range = 100f..500f,
      step = 7,
      unit = "MB",
      onClick = {
        //                context.clearCache()
        showClearCacheDialog = true
      },
      onValueChanged = {
        onSet(GeneralPreferenceKey.CacheSize, it)
      }
    )
      Spacer(modifier = Modifier.height(20.dp))
  }


  if (showClearCacheDialog) {
    AlertDialog(
      onDismissRequest = {
        showClearCacheDialog = false
      },
      confirmButton = {
        TextButton(onClick = {
          showClearCacheDialog = false
          scope.launch {
            getPlatform().clearCache()
          }
        }) {
          Text(text = stringResource(Res.string.confirm))
        }
      }, dismissButton = {

        TextButton(onClick = {
          showClearCacheDialog = false
        }) {
          Text(text = stringResource(Res.string.cancel))
        }

      }, title = {
        TextTitleLarge(text = stringResource(Res.string.are_you_clear_cache))
      })
  }

}

@Composable
private fun ThemeStylePicker(
  currentThemeStyle: AppThemeStyle,
  onThemeSelected: (AppThemeStyle) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val desktopWheelScroll = if (isDesktop()) {
    Modifier.pointerInput(listState) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Main)
          if (event.type == PointerEventType.Scroll) {
            val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
            val horizontalDelta = if (abs(delta.x) > 0.1f) delta.x else delta.y
            if (horizontalDelta != 0f) {
              scope.launch {
                listState.scrollBy(horizontalDelta * 60f)
              }
            }
          }
        }
      }
    }
  } else {
    Modifier
  }

  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 16.dp)
      .then(desktopWheelScroll),
    state = listState,
    contentPadding = PaddingValues(horizontal = 18.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    items(AppThemeStyle.entries.toList()) { style ->
      ThemeStyleButton(
        style = style,
        selected = style == currentThemeStyle,
        onClick = { onThemeSelected(style) }
      )
    }
  }
}

@Composable
private fun ThemeStyleButton(
  style: AppThemeStyle,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.focusable(),
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = if (selected) {
      MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    },
    tonalElevation = if (selected) 3.dp else 0.dp,
    shadowElevation = if (selected) 2.dp else 0.dp,
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
      }
    )
  ) {
    Column(
      modifier = Modifier
        .padding(8.dp)
        .width(64.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(style.previewColors()))
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = style.label(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun PreferenceSingleChoiceItem(
  modifier: Modifier = Modifier,
  text: String,
  selected: Boolean,
  contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 18.dp),
  onClick: () -> Unit
) {
  val performHaptic = rememberMobileHaptic()
  Surface(
    modifier = Modifier.selectable(
      selected = selected,
      onClick = {
        performHaptic()
        onClick()
      }
    )
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(contentPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 10.dp)
      ) {
        Text(
          text = text,
          maxLines = 1,
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
          color = MaterialTheme.colorScheme.onSurface,
          overflow = TextOverflow.Ellipsis
        )
      }
      RadioButton(
        selected = selected,
        onClick = {
          performHaptic()
          onClick()
        },
        modifier = Modifier
          .padding()
          .clearAndSetSemantics { },
      )
    }
  }
}
