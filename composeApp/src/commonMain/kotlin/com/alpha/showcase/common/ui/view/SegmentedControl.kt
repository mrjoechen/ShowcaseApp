package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * https://medium.com/@manojbhadane/hello-everyone-558290eb632e
 *
 * items : list of items to be render
 * defaultSelectedItemIndex : to highlight item by default (Optional)
 * useFixedWidth : set true if you want to set fix width to item (Optional)
 * itemWidth : Provide item width if useFixedWidth is set to true (Optional)
 * cornerRadius : To make control as rounded (Optional)
 * color : Set color to control (Optional)
 * onItemSelection : Get selected item index
 */
@Composable
fun SegmentedControl(
  items: List<String>,
  defaultSelectedItemIndex: Int = 0,
  useFixedWidth: Boolean = false,
  itemWidth: Dp = 120.dp,
  cornerRadius : Int = 20,
  color : Color = MaterialTheme.colorScheme.primary,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  onItemSelection: (selectedItemIndex: Int) -> Unit
) {
  val selectedIndex = remember(defaultSelectedItemIndex) { mutableStateOf(defaultSelectedItemIndex) }

  Row(
    modifier = Modifier
  ) {
    items.forEachIndexed { index, item ->
      OutlinedButton(
        modifier = when (index) {
          0 -> {
            if (useFixedWidth) {
              Modifier
                .width(itemWidth)
                .offset(0.dp, 0.dp)
                .zIndex(if (selectedIndex.value == index) 1f else 0f)
            } else {
              Modifier
                .wrapContentSize()
                .offset(0.dp, 0.dp)
                .zIndex(if (selectedIndex.value == index) 1f else 0f)
            }
          } else -> {
            if (useFixedWidth)
              Modifier
                .width(itemWidth)
                .offset((-1 * index).dp, 0.dp)
                .zIndex(if (selectedIndex.value == index) 1f else 0f)
            else Modifier
              .wrapContentSize()
              .offset((-1 * index).dp, 0.dp)
              .zIndex(if (selectedIndex.value == index) 1f else 0f)
          }
        }.onFocusChanged {
          if (it.isFocused){
            selectedIndex.value = index
            onItemSelection(selectedIndex.value)
          }
        }.focusable(true),
        onClick = {
          selectedIndex.value = index
          onItemSelection(selectedIndex.value)
        },
        shape = when (index) {
          /**
           * left outer button
           */
          0 -> RoundedCornerShape(
            topStartPercent = cornerRadius,
            topEndPercent = 0,
            bottomStartPercent = cornerRadius,
            bottomEndPercent = 0
          )
          /**
           * right outer button
           */
          items.size - 1 -> RoundedCornerShape(
            topStartPercent = 0,
            topEndPercent = cornerRadius,
            bottomStartPercent = 0,
            bottomEndPercent = cornerRadius
          )
          /**
           * middle button
           */
          else -> RoundedCornerShape(
            topStartPercent = 0,
            topEndPercent = 0,
            bottomStartPercent = 0,
            bottomEndPercent = 0
          )
        },
        border = BorderStroke(
          1.dp, if (selectedIndex.value == index) {
            color
          } else {
            color.copy(alpha = 0.75f)
          }
        ),
        colors = if (selectedIndex.value == index) {
          /**
           * selected colors
           */
          ButtonDefaults.outlinedButtonColors(
            containerColor = color
          )
        } else {
          /**
           * not selected colors
           */
          ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
        },
        contentPadding = contentPadding
      ) {
        Text(
          text = item,
          fontWeight = FontWeight.Normal,
          color = if (selectedIndex.value == index) {
            MaterialTheme.colorScheme.background
          } else {
            color.copy(alpha = 0.9f)
          },
        )
      }
    }
  }
}