package com.alpha.showcase.common.ui.ai

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.alpha.showcase.common.ai.AiEngine
import isWeb

internal const val AI_PROVIDER_ROUTE = "ai-providers"

internal fun NavGraphBuilder.aiProviderDestination(navController: NavHostController, engineOverride: AiEngine? = null) {
    if (isWeb()) return
    composable(AI_PROVIDER_ROUTE) {
        AiProviderPage(engineOverride = engineOverride, onDismiss = { navController.popBackStack() })
    }
}
