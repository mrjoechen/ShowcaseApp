package com.alpha.showcase.common.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.Image
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.ui.play.calculateVisibleImageBounds
import com.alpha.showcase.common.ui.play.calculateHorizontalRevealMask
import com.alpha.showcase.common.ui.settings.*
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.utils.ToastUtil
import isWeb
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

private val LocalAiPlaybackSettings = staticCompositionLocalOf<Settings?> { null }
private val LocalAiPlaybackActive = staticCompositionLocalOf { false }
private val LocalAiGenerate = staticCompositionLocalOf<((Image) -> Unit)?> { null }

@Composable
internal fun AiPlaybackContext(settings: Settings, active: Boolean, content: @Composable () -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) { content(); return }
    var source by remember { mutableStateOf<Image?>(null) }
    CompositionLocalProvider(LocalAiPlaybackSettings provides settings, LocalAiPlaybackActive provides active,
        LocalAiGenerate provides { image -> source = image }) { content() }
    source?.let { image -> AiGeneratorDialog(image) { source = null } }
}

@Composable
internal fun BoxScope.AiImageFeatures(image: Image?, data: Any, active: Boolean, editMode: Boolean,
    parentType: Int, fitSize: Boolean, showActions: Boolean) {
    if (!aiFeaturesAvailable(isWeb()) || editMode || image == null || !LocalAiPlaybackActive.current) return
    val generate = LocalAiGenerate.current ?: return
    val settings = LocalAiPlaybackSettings.current ?: return
//    if (active && showActions) Surface(
//        onClick = { generate(image) },
//        shape = RoundedCornerShape(16.dp),
//        color = Color.Black.copy(alpha = 0.6f),
//        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(48.dp)
//    ) {
//        Box(contentAlignment = Alignment.Center) {
//            Icon(
//                Icons.Outlined.AutoFixHigh,
//                stringResource(Res.string.ai_generate_action),
//                tint = Color.White,
//                modifier = Modifier.size(24.dp)
//            )
//        }
//    }
    if (!settings.isAiSummaryEnabled() || settings.showcaseMode != parentType) return
    val engine = remember { AiServices.engine }
    val library by engine.library.collectAsState()
    LaunchedEffect(engine) {
        try { engine.initialize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Configuration remains available in settings for recovery. */ }
    }
    val profile = library.activeProfiles.firstOrNull { it.id == library.understandingProfileId && aiProviderCapability(it.providerId) == com.alpha.ai.imagegeneration.AiCapability.IMAGE_UNDERSTANDING }
    if (profile == null && !library.facePrivacyEnabled) return
    val language = Locale.current.toLanguageTag()
    val key = aiSummaryKey(data, profile, language)
    val presentation = rememberAiSummaryPresentation(engine, key, image, profile, language, active)
    AiSummaryOverlay(presentation.state, image, fitSize, profile != null, presentation.regenerate)
}

internal fun Settings.isAiSummaryEnabled(): Boolean = when (showcaseMode) {
    SHOWCASE_MODE_SLIDE -> slideMode.enableAiImageSummary
    SHOWCASE_MODE_FADE -> fadeMode.enableAiImageSummary
    SHOWCASE_MODE_CALENDER -> calenderMode.enableAiImageSummary
    else -> false
}

internal const val AI_IMAGE_SUMMARY_KEY = "EnableAiImageSummary"

@Composable
internal fun AiSummarySwitch(enabled: Boolean, onCheck: (Boolean) -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    SwitchItem(Icons.Outlined.AutoAwesome, enabled, stringResource(Res.string.enable_ai_image_summary), onCheck)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSummaryOverlay(state: AiSummaryState, image: Image, fit: Boolean, hasProfile: Boolean, regenerate: () -> Unit) {
    // Privacy decisions precede all cached content, errors, and loading indicators.
    if (state.facePrivacyPending) return
    val showSummary = hasProfile && !state.facePrivacyBlocked && !state.facePrivacyUnavailable
    val content = state.content.takeIf { showSummary }
    val generating = showSummary && state.generating
    if (!state.facePrivacyBlocked && !state.facePrivacyUnavailable && content == null && !generating && !(showSummary && state.failed)) return
    val reveal = remember(content) { Animatable(0f) }
    LaunchedEffect(content) { if (content != null) reveal.animateTo(1f, tween(650, easing = LinearEasing)) }
    val appearance = remember(image, state.facePrivacyBlocked, state.facePrivacyUnavailable) { Animatable(0f) }
    LaunchedEffect(appearance) { appearance.animateTo(1f, tween(300)) }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = appearance.value }) {
        val bounds = calculateVisibleImageBounds(maxWidth.value, maxHeight.value, image.width.toFloat(), image.height.toFloat(), fit)
        val maxTextWidth = (bounds.width * if (maxWidth > maxHeight) 0.4f else 0.7f).dp
        Box(Modifier.offset(bounds.left.dp, bounds.top.dp).size(bounds.width.dp, bounds.height.dp).clipToBounds()
            .background(Brush.linearGradient(listOf(Color.Black.copy(0.46f), Color.Black.copy(0.16f), Color.Transparent),
                start = Offset(0f, Float.POSITIVE_INFINITY), end = Offset(Float.POSITIVE_INFINITY, 0f)))) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, end = 24.dp, bottom = 24.dp)
                .widthIn(max = maxTextWidth).then(
                    if (state.facePrivacyBlocked) Modifier
                    else Modifier.combinedClickable(onClick = {}, onDoubleClick = regenerate)
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.facePrivacyBlocked) {
                    val message = stringResource(Res.string.ai_image_summary_face_privacy_blocked)
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    Surface(onClick = { ToastUtil.toast(message) }, shape = CircleShape,
                        color = if (pressed) Color.Black.copy(alpha = 0.4f) else Color.Transparent,
                        interactionSource = interactionSource, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(Res.drawable.ic_face_privacy_checked), message,
                                tint = Color.White.copy(0.82f), modifier = Modifier.size(20.dp))
                        }
                    }
                } else if (state.facePrivacyUnavailable) {
                    Text(stringResource(Res.string.ai_image_summary_face_detection_failed), color = Color.White.copy(0.86f),
                        fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2)
                } else if (generating) {
                    val transition = rememberInfiniteTransition(label = "AiSummaryLoading")
                    val iconAlpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "AiSummaryLoadingAlpha",
                    )
                    Icon(Icons.Outlined.AutoAwesome, stringResource(Res.string.ai_image_summary_generating),
                        tint = Color.White.copy(0.82f),
                        modifier = Modifier.padding(top = 2.dp).size(18.dp).graphicsLayer { alpha = iconAlpha })
                } else if (content != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.AutoAwesome, stringResource(Res.string.ai_generated_badge), tint = Color.White.copy(0.82f), modifier = Modifier.padding(top = 2.dp).size(18.dp))
                    Column(Modifier.weight(1f).horizontalGradientReveal { reveal.value }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(content.narration, color = Color.White.copy(0.86f), fontSize = 16.sp, lineHeight = 22.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            content.tags.forEach { tag -> Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.24f),
                                border = BorderStroke(0.5.dp, Color.White.copy(0.24f))) {
                                Text(tag, color = Color.White.copy(0.84f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            } }
                        }
                    }
                } else Text(stringResource(Res.string.ai_image_summary_failed), color = Color.White.copy(0.86f), fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2)
            }
        }
    }
}

private fun Modifier.horizontalGradientReveal(progress: () -> Float): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val feather = 32.dp.toPx()
    onDrawWithContent {
        drawContent()
        val mask = calculateHorizontalRevealMask(size.width, progress(), feather)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent), startX = mask.startX, endX = mask.endX), blendMode = BlendMode.DstIn)
    }
}
