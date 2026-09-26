package com.alpha.showcase.common.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.Image
import com.alpha.showcase.common.ai.*
import com.alpha.showcase.common.ui.play.MediaItemState
import com.alpha.showcase.common.ui.play.MediaOverlayConfig
import androidx.compose.ui.layout.ContentScale
import com.alpha.showcase.common.ui.play.calculateVisibleImageBounds
import com.alpha.showcase.common.ui.play.calculateHorizontalRevealMask
import com.alpha.showcase.common.ui.play.mediaOverlaySummaryScrim
import com.alpha.showcase.common.ui.settings.*
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.rememberMobileHaptic
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.alpha.showcase.common.utils.ToastUtil
import isWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

internal val LocalAiPlaybackSettings = staticCompositionLocalOf<Settings?> { null }
private val LocalAiPlaybackActive = staticCompositionLocalOf { false }
private val LocalAiGenerate = staticCompositionLocalOf<((MediaItemState) -> Unit)?> { null }

@Composable
internal fun AiPlaybackContext(settings: Settings, active: Boolean, content: @Composable () -> Unit) {
    val aiAvailable = aiFeaturesAvailable(isWeb())
    val navigation = LocalAiNavigation.current
    if (aiAvailable && navigation == null) {
        AiNavigationHost { AiPlaybackContext(settings, active, content) }
        return
    }
    CompositionLocalProvider(LocalAiPlaybackSettings provides settings, LocalAiPlaybackActive provides active,
        LocalAiGenerate provides if (aiAvailable) navigation?.generateMedia else null) { content() }
}

@Composable
internal fun BoxScope.AiMediaOverlays(state: MediaItemState, active: Boolean,
    parentType: Int, config: MediaOverlayConfig) {
    val image = state.displayedImage ?: return
    if (!aiFeaturesAvailable(isWeb()) || !LocalAiPlaybackActive.current) return
    val settings = LocalAiPlaybackSettings.current ?: return
    if (settings.showcaseMode != parentType) return
    val generate = LocalAiGenerate.current
    if (LocalAiGenerationVisible.current && config.aiGenerate && generate != null) {
        androidx.compose.animation.AnimatedVisibility(
            visible = active && state.showActions,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(24.dp),
        ) {
            Surface(onClick = { if (active) generate(state) }, enabled = active, shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoFixHigh, stringResource(Res.string.ai_generate_action),
                        tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
    if (!config.aiSummary || !settings.isAiSummaryEnabled()) return
    val engine = remember { AiServices.engine }
    val library by engine.library.collectAsState()
    LaunchedEffect(engine) {
        try { engine.initialize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Settings remain available for recovery. */ }
    }
    val profile = library.activeProfiles.firstOrNull { it.id == library.understandingProfileId && aiProviderCapability(it.providerId) == com.alpha.ai.imagegeneration.AiCapability.IMAGE_UNDERSTANDING }
    val language = Locale.current.toLanguageTag()
    val presentation = rememberAiSummaryPresentation(engine, state.data, image, profile, language, active, state.metadata?.imageIdentity)
    AiSummaryOverlay(presentation.state, image, state.contentScale == ContentScale.Fit,
        profile != null, presentation.regenerate)
}

internal fun Settings.isAiSummaryEnabled(): Boolean = when (showcaseMode) {
    SHOWCASE_MODE_SLIDE -> slideMode.enableAiImageSummary
    SHOWCASE_MODE_FADE -> fadeMode.enableAiImageSummary
    SHOWCASE_MODE_CALENDER -> calenderMode.enableAiImageSummary
    else -> false
}

internal const val AI_IMAGE_SUMMARY_KEY = "EnableAiImageSummary"

@Composable
internal fun AiSummarySwitch(enabled: Boolean, engineOverride: AiEngine? = null, onCheck: (Boolean) -> Unit) {
    if (!aiFeaturesAvailable(isWeb())) return
    val engine = remember(engineOverride) { engineOverride ?: AiServices.engine }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val loadFailed = stringResource(Res.string.ai_profile_error_load_failed)
    val performHaptic = rememberMobileHaptic()
    val label = stringResource(Res.string.enable_ai_image_summary)
    val onToggle: (Boolean) -> Unit = { checked ->
        if (!checked) {
            onCheck(false)
        } else if (!checking) {
            checking = true
            scope.launch {
                try {
                    engine.initialize()
                    // Imported and previously generated summaries work without credentials.
                    onCheck(true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ToastUtil.toast(loadFailed)
                } finally {
                    checking = false
                }
            }
        }
    }
    IconItem(Icons.Outlined.AutoAwesome, label, onClick = { onToggle(!enabled) }) {
        Switch(
            checked = enabled,
            onCheckedChange = { performHaptic(); onToggle(it) },
            modifier = Modifier.padding(5.dp).semantics { contentDescription = label },
            thumbContent = if (enabled) {
                { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSummaryOverlay(state: AiSummaryState, image: Image, fit: Boolean, hasProfile: Boolean, regenerate: () -> Unit) {
    // Privacy decisions precede all cached content, errors, and loading indicators.
    if (state.facePrivacyPending) return
    val showSummary = !state.facePrivacyBlocked && !state.facePrivacyUnavailable
    val content = state.content.takeIf { showSummary }
    val generating = hasProfile && showSummary && state.generating
    val failed = hasProfile && showSummary && state.failed
    if (!state.facePrivacyBlocked && !state.facePrivacyUnavailable && content == null && !generating && !failed) return
    val reveal = remember(content) { Animatable(0f) }
    LaunchedEffect(content) { if (content != null) reveal.animateTo(1f, tween(650, easing = LinearEasing)) }
    val appearance = remember(image, state.facePrivacyBlocked, state.facePrivacyUnavailable) { Animatable(0f) }
    LaunchedEffect(appearance) { appearance.animateTo(1f, tween(300)) }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = appearance.value }) {
        val bounds = calculateVisibleImageBounds(maxWidth.value, maxHeight.value, image.width.toFloat(), image.height.toFloat(), fit)
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
        val leftInset = (safe.calculateLeftPadding(direction) - bounds.left.dp).coerceAtLeast(0.dp)
        val rightInset = (safe.calculateRightPadding(direction) - (maxWidth - (bounds.left + bounds.width).dp)).coerceAtLeast(0.dp)
        val bottomInset = (safe.calculateBottomPadding() - (maxHeight - (bounds.top + bounds.height).dp)).coerceAtLeast(0.dp)
        val maxTextWidth = (bounds.width * if (maxWidth > maxHeight) 0.4f else 0.7f).dp
        val showScrim = content != null || state.facePrivacyUnavailable || failed
        var captionSize by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.offset(bounds.left.dp, bounds.top.dp).size(bounds.width.dp, bounds.height.dp)
            .padding(start = leftInset, end = rightInset, bottom = bottomInset).clipToBounds()) {
            if (showScrim) {
                Box(Modifier.matchParentSize().mediaOverlaySummaryScrim(captionSize))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 24.dp, bottom = 16.dp)
                .onSizeChanged { captionSize = it }
                .widthIn(max = maxTextWidth).then(
                    if (state.facePrivacyBlocked) Modifier
                    else Modifier.clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = {}, onDoubleClick = if (hasProfile || state.facePrivacyUnavailable) regenerate else null)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
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
                } else if (content != null) {
                    AiSummaryContent(content.narration, content.tags,
                        textModifier = Modifier.horizontalGradientReveal { reveal.value })
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
