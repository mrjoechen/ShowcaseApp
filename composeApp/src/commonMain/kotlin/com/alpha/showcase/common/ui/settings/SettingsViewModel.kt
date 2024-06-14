package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.data.GeneralPreference
import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Created by chenqiao on 2023/9/19.
 * e-mail : mrjctech@gmail.com
 */

open class SettingsViewModel: BaseViewModel() {

  companion object: SettingsViewModel()

  init {
    viewModelScope.launch {
      getSettings()
      getGeneralSettings()
    }
  }

  private val settingRepo = SettingPreferenceRepo()

  private val generalPreferenceRepo = GeneralSettingsRepo()

  private val _settingsStateFlow = MutableStateFlow<UiState<Settings>>(UiState.Loading)
  val settingsFlow = _settingsStateFlow as StateFlow<UiState<Settings>>

  private val _generalStateFlow = MutableStateFlow<UiState<GeneralPreference>>(UiState.Loading)
  val generalPreferenceFlow = _generalStateFlow as StateFlow<UiState<GeneralPreference>>

  fun getGeneralSettings(): GeneralPreference {
    return GeneralPreference(1, 1)
  }

  private suspend fun getSettings() {
    _settingsStateFlow.emit(UiState.Content(settingRepo.getSettings()))
  }

  suspend fun updateSettings(update: (Settings) -> Settings) {
    val updateSettings = settingRepo.updateSettings {
      update(it)
    }
    _settingsStateFlow.emit(UiState.Content(updateSettings))

  }

  suspend fun updateSettings(settings: Settings){
    val updateSettings = settingRepo.updateSettings {
      settings
    }
    _settingsStateFlow.emit(UiState.Content(updateSettings))
  }

}