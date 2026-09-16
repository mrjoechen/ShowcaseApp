package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ai_generated_badge

/** Shared caption styling for playback and the configuration preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSummaryContent(
    narration: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.AutoAwesome, stringResource(Res.string.ai_generated_badge),
            tint = Color.White.copy(0.82f), modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Column(Modifier.weight(1f).then(textModifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(narration, color = Color.White.copy(0.86f), fontSize = 16.sp, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.24f),
                        border = BorderStroke(0.5.dp, Color.White.copy(0.24f))) {
                        Text(tag, color = Color.White.copy(0.84f), fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
