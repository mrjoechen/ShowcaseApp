package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.theme.AppThemeStyle
import kotlinx.serialization.Serializable

/**
 * Created by chenqiao on 2023/9/19.
 * e-mail : mrjctech@gmail.com
 */
sealed class GeneralPreferenceKey {

  companion object {
    const val Language: String = "Language"
    const val DarkMode: String = "DarkMode"
    const val ThemeStyle: String = "ThemeStyle"
    const val AnonymousUsage: String = "AnonymousUsage"
    const val CacheSize: String = "CacheSize"
    const val AutoCheckUpdate: String = "AutoCheckUpdate"
    const val LaunchOnBoot: String = "LaunchOnBoot"
    const val DisplayRotation: String = "DisplayRotation"
  }
}

@Serializable
data class GeneralPreference(
    val language: Int,
    val darkMode: Int,
    val themeStyle: Int = AppThemeStyle.default.value,
    val anonymousUsage: Boolean = ANONYMOUS_USAGE_DEFAULT,
    val anonymousUsageConsentVersion: Int = ANONYMOUS_USAGE_UNDECIDED,
    val cacheSize: Int = DEFAULT_CACHE_SIZE,
    val autoCheckUpdate: Boolean = AUTO_CHECK_UPDATE_DEFAULT,
    val launchOnBoot: Boolean = LAUNCH_ON_BOOT_DEFAULT,
    val displayRotation: Int = DEFAULT_DISPLAY_ROTATION,
    val latestSource: String = ""
) {
    val hasAnonymousUsageConsent: Boolean
        get() = anonymousUsage && !needsAnonymousUsageDecision

    /** No answer for the current consent version yet: nothing optional is collected, and the app asks. */
    val needsAnonymousUsageDecision: Boolean
        get() = anonymousUsageConsentVersion < ANONYMOUS_USAGE_CONSENT_VERSION

    fun decideAnonymousUsage(allowed: Boolean): GeneralPreference =
        copy(anonymousUsage = allowed, anonymousUsageConsentVersion = ANONYMOUS_USAGE_CONSENT_VERSION)
}

const val ANONYMOUS_USAGE_DEFAULT = false
const val ANONYMOUS_USAGE_UNDECIDED = 0
/** Version 2 asks every device once; earlier "on" values were mostly defaults, not answers. */
const val ANONYMOUS_USAGE_CONSENT_VERSION = 2
const val DEFAULT_CACHE_SIZE = 100
const val AUTO_CHECK_UPDATE_DEFAULT = true
const val LAUNCH_ON_BOOT_DEFAULT = false
const val DEFAULT_DISPLAY_ROTATION = 270
