package com.alpha.showcase.common.ui.ai

import androidx.compose.runtime.*
import coil3.Image
import com.alpha.showcase.common.ai.*
import kotlinx.coroutines.CancellationException

internal class AiSummaryPresentation(val state: AiSummaryState, val regenerate: () -> Unit)

/** A new displayed image owns a fresh state collector, even when its URL is unchanged. */
@Composable
internal fun rememberAiSummaryPresentation(
    engine: AiEngine, mediaKey: String, image: Image, profile: AiProfile?, language: String, active: Boolean,
): AiSummaryPresentation {
    val library by engine.library.collectAsState()
    val privacyEnabled = library.facePrivacyEnabled
    return key(engine, mediaKey, image, profile, language, privacyEnabled) {
        // Do not briefly render the default (privacy-off) state before stored settings load.
        var initialized by remember { mutableStateOf(false) }
        var request by remember { mutableStateOf<AiSummaryRequest?>(null) }
        var preparationFailed by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        LaunchedEffect(active, retry) {
            try { engine.initialize(); initialized = true }
            catch (e: CancellationException) { throw e }
            catch (_: Throwable) { preparationFailed = true; return@LaunchedEffect }
            if (active && request == null) {
                preparationFailed = false
                try { request = engine.summaries.prepare(mediaKey, image, profile, language) }
                catch (e: CancellationException) { throw e }
                catch (_: Throwable) { preparationFailed = true }
            }
        }
        val prepared = request
        val state = if (prepared == null) {
            when {
                preparationFailed && (!initialized || privacyEnabled) -> AiSummaryState(facePrivacyUnavailable = true)
                !initialized || privacyEnabled -> AiSummaryState(facePrivacyPending = true)
                else -> AiSummaryState(generating = active && profile != null && !preparationFailed, failed = preparationFailed)
            }
        } else {
            val current by remember(prepared) { engine.summaries.observe(prepared) }.collectAsState()
            LaunchedEffect(prepared, active, privacyEnabled) { if (active) engine.summaries.request(prepared) }
            current
        }
        AiSummaryPresentation(state) {
            if (active) {
                if (prepared == null) retry++
                else engine.summaries.request(prepared, force = true)
            }
        }
    }
}
