package com.alpha.showcase.common.ui.ai

import androidx.compose.runtime.*
import coil3.Image
import com.alpha.showcase.common.ai.*
import kotlinx.coroutines.CancellationException

internal class AiSummaryPresentation(val state: AiSummaryState, val regenerate: () -> Unit)

/** A new displayed image owns a fresh state collector, even when its URL is unchanged. */
@Composable
internal fun rememberAiSummaryPresentation(
    engine: AiEngine, mediaKey: String, image: Image, profile: AiProfile, language: String, active: Boolean,
): AiSummaryPresentation = key(engine, mediaKey, image, profile, language) {
    var request by remember { mutableStateOf<AiSummaryRequest?>(null) }
    var preparationFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, retry) {
        if (active && request == null) {
            preparationFailed = false
            try { request = engine.summaries.prepare(mediaKey, image, profile, language) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { preparationFailed = true }
        }
    }
    val prepared = request
    val state = if (prepared == null) {
        AiSummaryState(generating = active && !preparationFailed, failed = preparationFailed)
    } else {
        val current by remember(prepared) { engine.summaries.observe(prepared) }.collectAsState()
        LaunchedEffect(prepared, active) { if (active) engine.summaries.request(prepared) }
        current
    }
    AiSummaryPresentation(state) {
        if (active) {
            if (prepared == null) retry++
            else engine.summaries.request(prepared, force = true)
        }
    }
}
