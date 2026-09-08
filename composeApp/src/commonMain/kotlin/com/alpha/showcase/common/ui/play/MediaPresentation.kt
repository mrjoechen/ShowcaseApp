package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alpha.showcase.common.ui.settings.*

data class MediaOverlayConfig(
    val metadata: Boolean = true,
    val aiSummary: Boolean = false,
    val aiGenerate: Boolean = false,
) {
    fun restrictedToStyle(parentType: Int): MediaOverlayConfig =
        if (isMultiPhotoStyle(parentType)) None else this

    companion object {
        val None = MediaOverlayConfig(false, false, false)
        fun forStyle(parentType: Int) = MediaOverlayConfig(
            aiSummary = parentType in listOf(SHOWCASE_MODE_SLIDE, SHOWCASE_MODE_FADE, SHOWCASE_MODE_CALENDER),
            aiGenerate = true,
        ).restrictedToStyle(parentType)
    }
}

internal fun isMultiPhotoStyle(parentType: Int) = parentType in listOf(
    SHOWCASE_MODE_FRAME_WALL, SHOWCASE_MODE_WATERFALL, SHOWCASE_MODE_BENTO,
    SHOWCASE_MODE_SQUARE, SHOWCASE_MODE_CAROUSEL,
)

internal fun supportsKenBurns(parentType: Int) =
    parentType == SHOWCASE_MODE_FADE || parentType == SHOWCASE_MODE_CALENDER

/** Styles own composition: the effect slot must contain only the media renderer. */
@Composable
fun MediaPresentation(
    modifier: Modifier = Modifier,
    data: Any,
    fitSize: Boolean = false,
    parentType: Int = -1,
    active: Boolean = true,
    editMode: Boolean = false,
    overlayConfig: MediaOverlayConfig = MediaOverlayConfig.forStyle(parentType),
    mediaEffect: @Composable (@Composable () -> Unit) -> Unit = { it() },
    onImageDimensionsAvailable: (Int, Int) -> Unit = { _, _ -> },
    onComplete: (Any) -> Unit = {},
) {
    val state = rememberMediaItemState(data, fitSize)
    val config = if (editMode) MediaOverlayConfig.None else overlayConfig.restrictedToStyle(parentType)
    Box(modifier.mediaActivity { if (active) state.interact(config) }) {
        mediaEffect {
            PagerItem(
                state = state, modifier = Modifier.fillMaxSize(), active = active,
                readMetadata = config.metadata,
                onInteraction = { tap -> if (active) state.interact(config, tap) },
                onImageDimensionsAvailable = onImageDimensionsAvailable, onComplete = onComplete,
            )
        }
        MediaOverlays(state, config, active, parentType, editMode)
    }
}
