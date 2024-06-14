package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.launch


@Composable
fun SettingsListView(viewModel: SettingsViewModel = SettingsViewModel) {
    var uiState by remember {
        mutableStateOf<UiState<Settings>>(UiState.Loading)
    }
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        uiState.let {
            when (it) {
                UiState.Loading -> {
                    ProgressIndicator()
                }
                is UiState.Content -> SettingsColumn(
                    (uiState as UiState.Content<Settings>).data,
                    viewModel
                )

                else -> {}
            }


            viewModel.settingsFlow.collectAsState(UiState.Loading).value.let {
                uiState = it
            }
        }
    }
}


@Composable
fun SettingsColumn(settings: Settings, viewModel: SettingsViewModel) {

//    val settingState by remember(settings) {
//        mutableStateOf(settings)
//    }
//    val settingProvider by remember(settings) {
//        derivedStateOf {
//            settings.toBuilder()
//        }
//    }

    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .widthIn(max = 650.dp)
    ) {

        ShowcaseSettings(settings, viewModel.getGeneralSettings(), onSettingChanged = { settings ->
            coroutineScope.launch {
                viewModel.updateSettings(settings)
            }
        })
    }
}


@Composable
fun ProgressIndicator(size: Dp = 50.dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size)
        )
    }
}