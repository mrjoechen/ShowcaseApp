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
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


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
fun SettingsColumn(settings: Settings, preference: GeneralPreference, viewModel: SettingsViewModel) {

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

        ShowcaseSettings(settings, preference, onSettingChanged = { settings ->
            coroutineScope.launch {
                viewModel.updateSettings(settings)
            }
        }, onGeneralSettingChanged = { preference ->
            coroutineScope.launch {
                viewModel.updatePreference(preference)
            }
        })
    }
}
