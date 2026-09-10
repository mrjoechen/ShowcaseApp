package com.alpha.showcase.common.ui.ai

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.savedstate.read
import com.alpha.showcase.common.ai.AiEngine
import isWeb

internal const val AI_CREATIONS_ROUTE = "ai-creations"
internal const val AI_CREATION_PREVIEW_ROUTE = "ai-creation-preview"

/** Settings-owned pages share the image services back stack and platform transitions. */
internal fun NavGraphBuilder.aiCreationDestinations(navController: NavHostController, engineOverride: AiEngine? = null) {
    if (isWeb()) return
    composable(AI_CREATIONS_ROUTE) {
        AiCreationCenter(
            engineOverride = engineOverride,
            onOpenProviders = { navController.navigate(AI_PROVIDER_ROUTE) { launchSingleTop = true } },
            onPreview = { navController.navigate("$AI_CREATION_PREVIEW_ROUTE/$it") },
            onDismiss = { navController.popBackStack() },
        )
    }
    composable("$AI_CREATION_PREVIEW_ROUTE/{taskId}") { destination ->
        val taskId = destination.arguments?.read { getStringOrNull("taskId") }
        AiCreationPreview(taskId, engineOverride) { navController.popBackStack() }
    }
}
