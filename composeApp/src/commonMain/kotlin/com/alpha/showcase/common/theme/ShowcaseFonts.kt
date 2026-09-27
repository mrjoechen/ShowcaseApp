package com.alpha.showcase.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import org.jetbrains.compose.resources.Font
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.smileysans_oblique

@Composable
internal fun smileySansFontFamily(): FontFamily {
    val font = Font(Res.font.smileysans_oblique)
    return remember(font) { FontFamily(font) }
}

internal val showcaseOverlayTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.82f),
    offset = Offset(0f, 1f),
    blurRadius = 4f,
)
