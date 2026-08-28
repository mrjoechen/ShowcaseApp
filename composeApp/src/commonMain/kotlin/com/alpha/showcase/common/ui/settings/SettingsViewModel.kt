package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.theme.AppThemeStyle
import com.alpha.showcase.common.ui.settings.DarkThemePreference.Companion.FOLLOW_SYSTEM
import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.AnonymousUsageController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Created by chenqiao on 2023/9/19.
 * e-mail : mrjctech@gmail.com
 */

open class SettingsViewModel: BaseViewModel() {

  companion object: SettingsViewModel()

  val darkModeFlow = MutableStateFlow(FOLLOW_SYSTEM)
  val themeStyleFlow = MutableStateFlow(AppThemeStyle.default)

  init {
    viewModelScope.launch {
      getSettings()
      getGeneralSettings()
    }
  }

  private val settingRepo = SettingPreferenceRepo()
  private val preferenceUpdateMutex = Mutex()

  private val _settingsStateFlow = MutableStateFlow<UiState<Settings>>(UiState.Loading)
  val settingsFlow = _settingsStateFlow as StateFlow<UiState<Settings>>

  private val _generalStateFlow = MutableStateFlow<UiState<GeneralPreference>>(UiState.Loading)
  val generalPreferenceFlow = _generalStateFlow as StateFlow<UiState<GeneralPreference>>

  private suspend fun getGeneralSettings() {
    try {
      val preference = settingRepo.getPreference()
      darkModeFlow.emit(preference.darkMode)
      themeStyleFlow.emit(AppThemeStyle.fromValue(preference.themeStyle))
      _generalStateFlow.emit(UiState.Content(preference))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      error.printStackTrace()
      _generalStateFlow.emit(UiState.Error(error.message))
    }
  }

  private suspend fun getSettings() {
    try {
      _settingsStateFlow.emit(UiState.Content(settingRepo.getSettings()))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      error.printStackTrace()
      _settingsStateFlow.emit(UiState.Error(error.message))
    }
  }

  suspend fun updateSettings(settings: Settings){
    settingRepo.updateSettings(settings)
    _settingsStateFlow.emit(UiState.Content(settings))
  }

  suspend fun updatePreference(
    preference: GeneralPreference,
    expectedPreference: GeneralPreference? = null,
  ) {
    // Stop events on the initiating stack, even if another preference write currently owns the
    // mutex or the settings page is disposed immediately afterwards.
    val requestedOptOut = !preference.hasAnonymousUsageConsent &&
      expectedPreference?.hasAnonymousUsageConsent != false
    if (requestedOptOut) {
      AnonymousUsageController.beginConsentChange(false)
    }

    var completeOptOut = requestedOptOut
    try {
      preferenceUpdateMutex.withLock {
        val current = (_generalStateFlow.value as? UiState.Content<GeneralPreference>)
          ?.data
          ?: settingRepo.getPreference()
        val resolved = expectedPreference?.let { expected ->
          mergeGeneralPreferenceUpdate(current, expected, preference)
        } ?: preference
        val previousConsent = current.hasAnonymousUsageConsent
        val nextConsent = resolved.hasAnonymousUsageConsent
        val consentChanged = previousConsent != nextConsent

        if (consentChanged && !nextConsent) {
          completeOptOut = true
          AnonymousUsageController.beginConsentChange(false)
        }

        // Once a user action has started, page-scope cancellation must not leave the durable
        // preference and collector lifecycle disagreeing with each other.
        withContext(NonCancellable) {
          settingRepo.updatePreference(resolved)

          if (consentChanged && nextConsent) {
            AnonymousUsageController.beginConsentChange(true)
            AnonymousUsageController.completeConsentChange(true)
          }

          darkModeFlow.value = resolved.darkMode
          themeStyleFlow.value = AppThemeStyle.fromValue(resolved.themeStyle)
          _generalStateFlow.value = UiState.Content(resolved)
        }
      }
    } finally {
      if (completeOptOut) {
        // This cleanup is privacy-critical and must outlive the composable that initiated it.
        withContext(NonCancellable) {
          AnonymousUsageController.completeConsentChange(false)
        }
      }
    }
  }

}

/**
 * Applies only fields changed by [updated] relative to the UI snapshot [expected]. This prevents
 * two rapid settings actions from restoring stale values written by the other action.
 */
internal fun mergeGeneralPreferenceUpdate(
  current: GeneralPreference,
  expected: GeneralPreference,
  updated: GeneralPreference,
): GeneralPreference {
  val consentChanged = updated.anonymousUsage != expected.anonymousUsage ||
    updated.anonymousUsageConsentVersion != expected.anonymousUsageConsentVersion
  return current.copy(
    language = if (updated.language != expected.language) updated.language else current.language,
    darkMode = if (updated.darkMode != expected.darkMode) updated.darkMode else current.darkMode,
    themeStyle = if (updated.themeStyle != expected.themeStyle) updated.themeStyle else current.themeStyle,
    anonymousUsage = if (consentChanged) updated.anonymousUsage else current.anonymousUsage,
    anonymousUsageConsentVersion = if (consentChanged) {
      updated.anonymousUsageConsentVersion
    } else {
      current.anonymousUsageConsentVersion
    },
    cacheSize = if (updated.cacheSize != expected.cacheSize) updated.cacheSize else current.cacheSize,
    autoCheckUpdate = if (updated.autoCheckUpdate != expected.autoCheckUpdate) {
      updated.autoCheckUpdate
    } else {
      current.autoCheckUpdate
    },
    latestSource = if (updated.latestSource != expected.latestSource) {
      updated.latestSource
    } else {
      current.latestSource
    },
  )
}
