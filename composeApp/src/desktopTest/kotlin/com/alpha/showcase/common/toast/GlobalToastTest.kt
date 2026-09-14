package com.alpha.showcase.common.toast

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlobalToastTest {
    @Test
    fun ordinaryToastIsNotShownByGlobalHost() = runComposeUiTest {
        try {
            ToastManager.currentToastFlow.value = ToastMessage(message = "Local message", source = "test")
            setContent { MaterialTheme { ToastHost(scope = ToastScope.GLOBAL) } }
            onAllNodesWithText("Local message").assertCountEquals(0)
        } finally {
            ToastManager.currentToastFlow.value = null
        }
    }

    @Test
    fun globalToastSurvivesContentPageRemoval() = runComposeUiTest {
        val configurationOpen = mutableStateOf(false)
        try {
            ToastManager.currentToastFlow.value = ToastMessage(
                message = "Configure image understanding API", source = "test", scope = ToastScope.GLOBAL,
            )
            setContent {
                MaterialTheme {
                    Box {
                        if (!configurationOpen.value) ToastHost()
                        else Text("API configuration")
                        ToastHost(scope = ToastScope.GLOBAL)
                    }
                }
            }
            onAllNodesWithText("Configure image understanding API").assertCountEquals(1)
            onNodeWithText("Configure image understanding API").assertIsDisplayed()
            runOnIdle { configurationOpen.value = true }
            onNodeWithText("API configuration").assertIsDisplayed()
            onNodeWithText("Configure image understanding API").assertIsDisplayed()
            runOnIdle { ToastManager.currentToastFlow.value = null }
            onAllNodesWithText("Configure image understanding API").assertCountEquals(0)
        } finally {
            ToastManager.currentToastFlow.value = null
        }
    }
}
