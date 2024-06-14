package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.data.DarkThemePreference
import com.alpha.showcase.common.data.GeneralPreference
import com.alpha.showcase.common.data.GeneralPreferenceKey
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.SegmentedControl
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.ui.view.TextTitleMedium


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


  Column {
    TextTitleMedium(text = "General")

    IconItem(Icons.Outlined.Language, desc = "Language", onClick = {
      showLanguageSelectDialog = !showLanguageSelectDialog
    }){
      Text(text = "Language", color = LocalContentColor.current.copy(0.6f))
    }

    //
    IconItem(Icons.Outlined.Bedtime, desc = "Dark mode", onClick = {

    }){

      SegmentedControl(
        items = DarkThemePreference.darkThemeChoices(),
        defaultSelectedItemIndex = generalPreference.darkMode,
        contentPadding = PaddingValues(10.dp, 4.dp),
      ) {
        onSet(GeneralPreferenceKey.DarkMode, it)
      }

    }

    SwitchItem(
      Icons.Outlined.Analytics,
      check = generalPreference.anonymousUsage,
      desc = "AnonymousUsage",
      onCheck = {
        onSet(GeneralPreferenceKey.AnonymousUsage, it)
      })

    SlideItem(
      Icons.Outlined.DonutLarge,
      desc = "Cache Size",
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
      })

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
  Surface(
    modifier = Modifier.selectable(
      selected = selected, onClick = onClick
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
        onClick = onClick,
        modifier = Modifier
          .padding()
          .clearAndSetSemantics { },
      )
    }
  }
}
