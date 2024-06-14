package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.floor


val ITEM_PADDING_HORIZONTAL = 18.dp
val ITEM_PADDING_VERTICAL = 12.dp


@Composable
fun CommonItem(desc: String, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier
      .padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL)
      .wrapContentHeight()
      .fillMaxWidth(),
    verticalAlignment = CenterVertically
  ) {
    Text(
      text = desc, modifier = Modifier
        .weight(1f)
        .padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL)
    )
    content()
  }
}

@Composable
fun IconItem(icon: Any, desc: String, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit = {}) {

  if (onClick == null) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(),
      shape = CardDefaults.shape
    ) {
      ItemContent(icon = icon, desc = desc) {
        content()
      }
    }
  } else {
    Surface(
      modifier = Modifier
        .fillMaxWidth(),
      shape = CardDefaults.shape,
      onClick = {
        onClick()
      }
    ) {
      ItemContent(icon = icon, desc = desc) {
        content()
      }
    }
  }

}

@Composable
fun ItemContent(icon: Any?, desc: String, content: @Composable (RowScope.() -> Unit)? = null) {
  Row(
    modifier = Modifier
      .padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL)
      .fillMaxWidth(),
    verticalAlignment = CenterVertically
  ) {

    when (icon) {
      is ImageVector -> {
        Icon(
          imageVector = icon,
          contentDescription = desc,
          modifier = Modifier
            .padding(5.dp)
        )
      }

      is Painter -> {
        Icon(
          painter = icon,
          contentDescription = desc,
          modifier = Modifier
            .padding(5.dp)
        )
      }

      is DrawableResource -> {
        Icon(
          painter = painterResource(icon),
          contentDescription = desc,
          modifier = Modifier
            .padding(5.dp)
        )
      }

      else -> {
//        CircularProgressIndicator(
//          modifier = Modifier
//            .padding(5.dp)
//        )
      }
    }

    Text(
      text = desc, modifier = Modifier
        .weight(1f)
        .padding(15.dp)
    )
    content?.invoke(this)
  }
}


@Composable
fun SwitchItem(icon: Any, check: Boolean, desc: String, onCheck: (Boolean) -> Unit) {
  var checked by remember {mutableStateOf(check)}

  val thumbContent: (@Composable () -> Unit)? = if (checked) {
    {
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        modifier = Modifier.size(SwitchDefaults.IconSize),
      )
    }
  } else {
    null
  }

  IconItem(icon = icon, desc = desc, onClick = {
    checked = ! checked
    onCheck(checked)
  }) {
    Switch(
      modifier = Modifier
        .padding(5.dp)
        .semantics { contentDescription = desc },
      checked = checked,
      onCheckedChange = {
        checked = it
        onCheck(checked)
      },
      thumbContent = thumbContent)
  }
}

@Composable
fun <T> CheckItem(icon: Any, value: Pair<T, String>, desc: String, choices: List<Pair<T, String>>, onCheck: (Pair<T, String>) -> Unit) {
  var expanded by remember {mutableStateOf(false)}
  var check by remember {mutableStateOf(value)}

  val checkString by remember {
    derivedStateOf {
      check.second
    }
  }


  IconItem(icon = icon, desc = desc, onClick = {
    expanded = ! expanded
  }) {

    Text(text = checkString)
    Box {
      IconButton(onClick = {expanded = ! expanded}) {
        Icon(Icons.Outlined.ArrowRight, contentDescription = desc)
      }
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = {expanded = false}
      ) {

        choices.forEachIndexed {index, item ->
          DropdownMenuItem(
            text = {Text(item.second, modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)},
            onClick = {
              expanded = false
              check = item
              onCheck(item)
            },
            //            leadingIcon = {
            //              Icon(
            //                Icons.Outlined.FiberManualRecord,
            //                contentDescription = checkString
            //              )
            //            }
          )
        }
      }
    }
  }
}

@Composable
fun SlideItem(
  icon: Any,
  trigger: Boolean? = null,
  onCheck: ((Boolean) -> Unit)? = null,
  desc: String,
  value: Int,
  range: ClosedFloatingPointRange<Float>,
  unit: String = "",
  step: Int = 0,
  onClick: (() -> Unit)? = null,
  onValueChanged: (Int) -> Unit,
) {

  var checked by remember {
    mutableStateOf(trigger ?: true)
  }


  val thumbContent: (@Composable () -> Unit)? = if (checked) {
    {
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        modifier = Modifier.size(SwitchDefaults.IconSize),
      )
    }
  } else {
    null
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth(),
    shape = CardDefaults.shape
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {

      var sliderPosition by remember(value) {mutableStateOf(value)}
      val text = if (checked) "$sliderPosition $unit" else ""

      if (trigger == null) {
        IconItem(icon = icon, desc = desc, onClick = onClick) {
          Text(
            text = if (checked) "${sliderPosition.toInt()} $unit" else "",
            Modifier.padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL)
          )
        }
      } else {
        Surface(modifier = Modifier
          .background(Color.Transparent)
          .fillMaxWidth()
          .wrapContentHeight(), onClick = {
          checked = ! checked
          onCheck?.invoke(checked)
        }) {
          ItemContent(icon = icon, desc = desc) {
            Text(text = text, Modifier.padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL))
            Switch(
              modifier = Modifier
                .padding(5.dp)
                .semantics { contentDescription = desc },
              checked = checked,
              onCheckedChange = {
                checked = it
                onCheck?.invoke(checked)
//                if (checked) {
//                  onValueChanged.invoke(value)
//                }
              },
              thumbContent = thumbContent)
          }
        }
      }


      var sliderFocused by remember{
        mutableStateOf(false)
      }

      Slider(
        modifier = Modifier
          .semantics { contentDescription = text }
          .padding(ITEM_PADDING_HORIZONTAL / 2 * 3, 0.dp).onFocusChanged {
             sliderFocused = it.isFocused
          }.onKeyEvent {
            if (sliderFocused){
              when {
                (Key.SystemNavigationLeft == it.key || it.key.keyCode == 90194313216) -> {

                  if (KeyEventType.KeyUp == it.type){
                    val toInt = sliderPosition - floor((range.endInclusive - range.start) / (step + 1)).toInt()
                    sliderPosition = if (toInt <= range.start) range.start.toInt() else toInt
                    onValueChanged(sliderPosition)
                    true
                  }else {
                    true
                  }
                }

                (Key.SystemNavigationRight == it.key || it.key.keyCode == 94489280512) -> {
                  if (KeyEventType.KeyUp == it.type){
                    val toInt = sliderPosition + floor((range.endInclusive - range.start) / (step + 1)).toInt()
                    sliderPosition = if (toInt >= range.endInclusive) range.endInclusive.toInt() else toInt
                    onValueChanged(sliderPosition)
                    true
                  }else {
                    true
                  }

                }

                else -> false
              }
            }else {
              false
            }

          },
        value = sliderPosition.toFloat(),
        onValueChange = {
          sliderPosition = it.toInt()
          onValueChanged(it.toInt())
        },
        valueRange = range,
        onValueChangeFinished = {
          // launch some business logic update with the state you hold
          // viewModel.updateSelectedSliderValue(sliderPosition)
        },
        steps = step,
        enabled = checked
      )

    }
  }
}

@Composable
fun <T> MultiCheckContent(icon: Any, desc: String, checkContent: List<List<Pair<T, String>>>, checkContentDesc: List<String>, checked: List<Pair<T, String>>, checkIcons: List<Any>, onCheckChanged: ((Int, Pair<T, String>) -> Unit), onCheckResult: (List<Pair<T, String>>) -> String){
  
  Surface {
    val resultList = remember {
      checked.toMutableStateList()
    }

    var result by remember {
      mutableStateOf(onCheckResult.invoke(checked))
    }
    
    Column {
      ItemContent(icon = icon, desc) {
        Text(text = result, Modifier.padding(ITEM_PADDING_HORIZONTAL, ITEM_PADDING_VERTICAL))
      }
      
      checkContent.forEachIndexed { index, item ->
        CheckItem(icon = checkIcons[index] , value = checked[index], desc = checkContentDesc[index], choices = item){
          resultList[index] = it
          onCheckChanged.invoke(index, it)
          result = onCheckResult.invoke(resultList.toList())
        }
      }
      
    }
    
  }

}

