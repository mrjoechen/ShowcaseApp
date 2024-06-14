package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigTitle(title: String, content: @Composable ColumnScope.() -> Unit, leftIcon: @Composable () -> Unit = {}, rightIcon: @Composable RowScope.() -> Unit = {}) {
  Column(modifier = Modifier.fillMaxSize()) {
    CenterAlignedTopAppBar(
      navigationIcon = leftIcon,
      title = {
        Text(
          text = title,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.background,
          modifier = Modifier.padding(10.dp)
        )
      },
      actions = rightIcon,
      colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
    )

    content()
  }
}