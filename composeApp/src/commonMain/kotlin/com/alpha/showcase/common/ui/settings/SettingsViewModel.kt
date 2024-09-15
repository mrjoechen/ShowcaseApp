package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.ui.settings.DarkThemePreference.Companion.FOLLOW_SYSTEM
import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Created by chenqiao on 2023/9/19.
 * e-mail : mrjctech@gmail.com
 */

open class SettingsViewModel: BaseViewModel() {

  companion object: SettingsViewModel()

  val darkModeFlow = MutableStateFlow(FOLLOW_SYSTEM)

  init {
    viewModelScope.launch {
      getSettings()
      getGeneralSettings()
    }
  }

  private val settingRepo = SettingPreferenceRepo()

  private val _settingsStateFlow = MutableStateFlow<UiState<Settings>>(UiState.Loading)
  val settingsFlow = _settingsStateFlow as StateFlow<UiState<Settings>>

  private val _generalStateFlow = MutableStateFlow<UiState<GeneralPreference>>(UiState.Loading)
  val generalPreferenceFlow = _generalStateFlow as StateFlow<UiState<GeneralPreference>>

  private suspend fun getGeneralSettings() {
    _generalStateFlow.emit(UiState.Content(settingRepo.getPreference()))
    darkModeFlow.emit(settingRepo.getPreference().darkMode)
  }

  private suspend fun getSettings() {
    _settingsStateFlow.emit(UiState.Content(settingRepo.getSettings()))
  }

  suspend fun updateSettings(settings: Settings){
    settingRepo.updateSettings(settings)
    _settingsStateFlow.emit(UiState.Content(settings))
  }

  suspend fun updatePreference(preference: GeneralPreference){
    settingRepo.updatePreference(preference)
    _generalStateFlow.emit(UiState.Content(preference))
    darkModeFlow.emit(preference.darkMode)
  }

}