package com.alpha.showcase.common.ui.settings

import kotlinx.serialization.Serializable

/**
 * Created by chenqiao on 2023/9/19.
 * e-mail : mrjctech@gmail.com
 */
sealed class GeneralPreferenceKey {

  companion object {
    const val Language: String = "Language"
    const val DarkMode: String = "DarkMode"
    const val AnonymousUsage: String = "AnonymousUsage"
    const val CacheSize: String = "CacheSize"
  }
}

@Serializable
data class GeneralPreference(
    val language: Int,
    val darkMode: Int,
    val anonymousUsage: Boolean = ANONYMOUS_USAGE_DEFAULT,
    val cacheSize: Int = DEFAULT_CACHE_SIZE,
    val latestSource: String = ""
)

const val ANONYMOUS_USAGE_DEFAULT = true
const val DEFAULT_CACHE_SIZE = 100