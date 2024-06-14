package com.alpha.showcase.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.ui.settings.SettingsListView
import com.alpha.showcase.common.ui.source.SourceListView
import isDesktop
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_name
import showcaseapp.composeapp.generated.resources.settings
import showcaseapp.composeapp.generated.resources.sources


/**
 * Created by chenqiao on 2022/12/25.
 * e-mail : mrjctech@gmail.com
 */

sealed class Screen(val route: StringResource, val icon: ImageVector) {
  data object Sources : Screen(Res.string.sources,  Icons.Outlined.Folder)
  data object Settings : Screen(Res.string.settings,  Icons.Outlined.Settings)
}

val navItems = listOf(
    Screen.Sources,
    Screen.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost() {

  var currentDestination by remember {
      mutableStateOf(Screen.Sources.route)
  }
  Scaffold(
    modifier = Modifier
        .fillMaxSize(),
    topBar = {
      if (!isDesktop()){
        TopAppBar(
          title = {
            Text(
              text = stringResource(Res.string.app_name),
              fontStyle = FontStyle.Italic,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.background,
              fontWeight = FontWeight.Bold
            )
          },
          colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
          //             scrollBehavior = scrollBehavior
        )
      }else {
        Column(
          Modifier
            .fillMaxWidth()
            .padding(5.dp, 20.dp, 5.dp, 5.dp), horizontalAlignment = Alignment.Start){

          Row(
            Modifier
              .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {

            Surface(
              Modifier.padding(16.dp, 20.dp),
              shape = RoundedCornerShape(6.dp),
            ) {

              Box(modifier = Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null) {
                currentDestination = Screen.Sources.route
              }) {
                Text(
                  modifier = Modifier.padding(20.dp, 10.dp),
                  text = stringResource(Res.string.app_name),
                  fontStyle = FontStyle.Italic,
                  fontSize = 32.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold
                )
              }

            }

            var settingSelected by remember {
              mutableStateOf(false)
            }.apply {
              value = Screen.Settings.route == currentDestination
            }

            Surface(
              Modifier.padding(20.dp, 0.dp),
              shape = RoundedCornerShape(6.dp),
              tonalElevation = if (settingSelected) 1.dp else 0.dp,
              shadowElevation = if (settingSelected) 1.dp else 0.dp
            ) {
              Box(modifier = Modifier
                .clickable {
                  settingSelected = !settingSelected
                  if (settingSelected){
                    currentDestination = Screen.Settings.route
                  }else {
                    currentDestination = Screen.Sources.route
                  }
                }
                .padding(10.dp)) {
                Icon(
                  imageVector = if (settingSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                  contentDescription = stringResource(Screen.Settings.route),
                  tint = if (settingSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
              }

            }

          }

        }
      }

    },
    bottomBar = {
      if (!isDesktop()){
        Column {
          NavigationBar {
            navItems.forEachIndexed {_, item ->
              NavigationBarItem(
                icon = {Icon(item.icon, contentDescription = stringResource(item.route))},
                label = {Text(stringResource(item.route))},
                selected = currentDestination == item.route,
                onClick = {
                  currentDestination = item.route
                }
              )
            }
          }
        }
      }
    }
  ){
    Column {
      Spacer(Modifier.height(it.calculateTopPadding()))
      if (currentDestination == Screen.Sources.route) {
        SourceListView()
      }
      if (currentDestination == Screen.Settings.route) {
        SettingsListView()
      }
    }

  }
}
