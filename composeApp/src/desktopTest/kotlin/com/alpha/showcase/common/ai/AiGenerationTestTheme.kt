package com.alpha.showcase.common.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.alpha.showcase.common.ui.ai.LocalAiGenerationVisible

/** Keep testing retained generation flows while their production entries are hidden. */
@Composable
internal fun AiGenerationTestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAiGenerationVisible provides true) {
        MaterialTheme(content = content)
    }
}
