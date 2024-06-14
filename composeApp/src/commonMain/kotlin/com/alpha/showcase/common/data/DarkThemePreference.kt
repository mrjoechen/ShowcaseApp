package com.alpha.showcase.common.data

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable


val darkThemeList = listOf(
    "On",
    "System",
    "Off"
)

data class DarkThemePreference(
    val darkThemeValue: Int = FOLLOW_SYSTEM,
    val isHighContrastModeEnabled: Boolean = false
) {
    companion object {
        const val ON = 0
        const val FOLLOW_SYSTEM = 1
        const val OFF = 2

        @Composable
        fun darkThemeChoices(): List<String> {
            return darkThemeList
        }
    }

    fun getIndex(): Int {
        return when (darkThemeValue) {
            FOLLOW_SYSTEM -> 1
            ON -> 0
            else -> 2
        }
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return if (darkThemeValue == FOLLOW_SYSTEM)
            isSystemInDarkTheme()
        else darkThemeValue == ON
    }

    @Composable
    fun getDarkThemeDesc(): String {
        return when (darkThemeValue) {
            FOLLOW_SYSTEM -> darkThemeList[1]
            ON -> darkThemeList[0]
            else -> darkThemeList[2]
        }
    }

}