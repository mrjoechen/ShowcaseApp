package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_github_lite
import showcaseapp.composeapp.generated.resources.ic_kofi
import showcaseapp.composeapp.generated.resources.ic_x


@Composable
fun SettingsListView(viewModel: SettingsViewModel = SettingsViewModel) {
    val combinedState by remember(viewModel) {
        combine(
            viewModel.settingsFlow,
            viewModel.generalPreferenceFlow
        ) { settingsState, preferenceState ->
            if (settingsState is UiState.Content && preferenceState is UiState.Content) {
                UiState.Content(Pair(settingsState.data, preferenceState.data))
            } else if (settingsState is UiState.Error || preferenceState is UiState.Error) {
                UiState.Error("Error loading data")
            } else {
                UiState.Loading
            }
        }
    }.collectAsState(initial = UiState.Loading)

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (val state = combinedState) {
            is UiState.Loading -> {
                CircleLoadingIndicator()
            }

            is UiState.Content -> {
                val (settings, preference) = state.data
                SettingsColumn(settings, preference, viewModel)
            }

            is UiState.Error -> {
            }
        }
    }
}


@Composable
fun SettingsColumn(
    settings: Settings,
    preference: GeneralPreference,
    viewModel: SettingsViewModel
) {

//    val settingState by remember(settings) {
//        mutableStateOf(settings)
//    }
//    val settingProvider by remember(settings) {
//        derivedStateOf {
//            settings.toBuilder()
//        }
//    }

    val coroutineScope = rememberCoroutineScope()

    Surface(modifier = Modifier.widthIn(max = 650.dp)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            ShowcaseSettings(settings, preference,
                onSettingChanged = { settings ->
                    coroutineScope.launch {
                        viewModel.updateSettings(settings)
                    }
               },
                onGeneralSettingChanged = { preference ->
                    coroutineScope.launch {
                        viewModel.updatePreference(preference)
                    }
                }
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uriHandler = LocalUriHandler.current
                IconButton(
                    onClick = {
                        uriHandler.openUri("https://x.com/chenqiao1104")
                    }
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_x),
                        contentDescription = "X"
                    )
                }


//            IconButton(
//                onClick = {
//                    openUrl("https://weibo.com/u/2208571963")
//                }
//            ) {
//                Icon(
//                    painterResource(Res.drawable.ic_weibo),
//                    contentDescription = "Weibo"
//                )
//            }

                IconButton(
                    onClick = {
                        uriHandler.openUri("https://github.com/mrjoechen")
                    }
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_github_lite),
                        contentDescription = "GitHub"
                    )
                }

                IconButton(
                    onClick = {
                        uriHandler.openUri("http://mrjoechen.github.io/showcase-site/donate.html")
                    }
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_kofi),
                        contentDescription = "Donate"
                    )
                }

//            Image(
//                painter = painterResource(Res.drawable.ic_buy_me_coffee),
//                modifier = Modifier.width(144.dp).height(36.dp)
//                    .clip(RoundedCornerShape(12.dp))
//                    .clickable { openUrl(buyMeCoffee) }, contentDescription = "Buy me a coffee")
//

            }

            Spacer(Modifier.height(20.dp))

        }
    }

}
