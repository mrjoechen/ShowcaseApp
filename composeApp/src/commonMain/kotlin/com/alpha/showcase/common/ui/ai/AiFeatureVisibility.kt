package com.alpha.showcase.common.ui.ai

import androidx.compose.runtime.staticCompositionLocalOf

/** Temporarily hide generation entry points while retaining saved profiles and creations. */
internal val LocalAiGenerationVisible = staticCompositionLocalOf { false }
