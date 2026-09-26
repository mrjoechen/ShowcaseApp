package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.play.mediaOverlaySummaryScrim
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

private data class SummaryPreviewPhoto(
    val image: DrawableResource,
    val description: StringResource,
    val narrations: StringArrayResource,
    val defaultNarrationIndex: Int,
    val tags: List<StringResource>,
)

// Photos by Joe Chen: joe-chen-A7sltwbY8WE-unsplash.jpg and joe-chen-hYcmWR6cGwk-unsplash.jpg.
private val summaryPreviewPhotos = listOf(
    SummaryPreviewPhoto(Res.drawable.ai_summary_preview_city,
        Res.string.ai_summary_preview_city_description, Res.array.ai_summary_preview_city_narrations, 1,
        listOf(Res.string.ai_summary_preview_tag_street, Res.string.ai_summary_preview_tag_sunset, Res.string.ai_summary_preview_tag_light)),
    SummaryPreviewPhoto(Res.drawable.ai_summary_preview_autumn,
        Res.string.ai_summary_preview_autumn_description, Res.array.ai_summary_preview_autumn_narrations, 3,
        listOf(Res.string.ai_summary_preview_tag_autumn, Res.string.ai_summary_preview_tag_branches, Res.string.ai_summary_preview_tag_water)),
)

/** Local feature examples, available before a service is configured. */
@Composable
internal fun AiSummaryPreview() {
    val pager = rememberPagerState(pageCount = { summaryPreviewPhotos.size })
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Dimen.textFiledCorners))) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()
                .summaryPreviewMouseDrag(pager, scope, layoutDirection)
                .testTag("ai-summary-preview-pager")) { page ->
                val photo = summaryPreviewPhotos[page]
                val narrations = stringArrayResource(photo.narrations)
                var narrationIndex by rememberSaveable(photo.image) { mutableIntStateOf(photo.defaultNarrationIndex) }
                val nextNarration = { narrationIndex = (narrationIndex + 1) % narrations.size }
                val nextNarrationLabel = stringResource(Res.string.ai_summary_preview_next_narration)
                var captionSize by remember { mutableStateOf(IntSize.Zero) }
                Box(Modifier.fillMaxSize()
                    .pointerInput(narrations.size) { detectTapGestures(onDoubleTap = { nextNarration() }) }
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(CustomAccessibilityAction(nextNarrationLabel) {
                            nextNarration()
                            true
                        })
                    }) {
                    Image(
                        painterResource(photo.image),
                        contentDescription = stringResource(photo.description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(Modifier.matchParentSize().mediaOverlaySummaryScrim(captionSize))
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .onSizeChanged { captionSize = it }
                            .padding(16.dp)
                            .widthIn(max = 440.dp),
                    ) {
                        AiSummaryContent(
                            narration = narrations[narrationIndex],
                            tags = photo.tags.map { stringResource(it) },
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.32f),
                contentColor = Color.White,
            ) {
                Text(stringResource(Res.string.ai_summary_preview_example, pager.currentPage + 1, pager.pageCount),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).selectableGroup()) {
            repeat(pager.pageCount) { page ->
                val selected = pager.currentPage == page
                val label = stringResource(Res.string.ai_summary_preview_example, page + 1, pager.pageCount)
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(width = 24.dp, height = 16.dp).semantics { contentDescription = label }
                        .selectable(selected = selected, role = Role.Tab,
                            interactionSource = interactionSource, indication = null,
                            onClick = { scope.launch { pager.animateScrollToPage(page) } }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape)
                        .indication(interactionSource, ripple()))
                }
            }
        }
        Text(stringResource(Res.string.ai_summary_preview_description),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
