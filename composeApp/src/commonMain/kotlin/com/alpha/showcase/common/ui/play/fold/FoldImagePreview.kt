package com.alpha.showcase.common.ui.play.fold

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt
import kotlin.math.abs
import com.alpha.showcase.common.components.BackHandler

private enum class FoldPlayback { Paused, Once, Loop, Rewind }
private val PreviewBackground = Color(0xFF141716)
private val PreviewAccent = Color(0xFFD7E3B8)

private val PhotoTitles = listOf(
    "山脊 · Alpine ridge", "湖畔 · Still water", "林间 · Into the green",
    "海岸 · Along the shore", "沙丘 · Desert light", "薄雾 · Morning mist",
    "山谷 · Open horizons", "高山湖 · Alpine lake",
)

internal val FoldDemoPhotoUrls = listOf(
    "photo-1464822759023-fed622ff2c3b",
    "photo-1470770841072-f978cf4d019e",
    "photo-1441974231531-c6227db76b6e",
    "photo-1507525428034-b723cf961d3e",
    "photo-1509316785289-025f5b846b35",
    "photo-1470071459604-3b5ec3a7fe05",
    "photo-1469474968028-56623f02e42e",
    "photo-1501785888041-af3ef285b470",
).map { "https://images.unsplash.com/$it?auto=format&fit=crop&w=1600&q=85" }

/** URL-backed evaluation screen; no navigation, account, or media-source setup. */
@Composable
fun FoldImageDemo(modifier: Modifier = Modifier, initialProgress: Float = 0f) {
    if (LocalInspectionMode.current) {
        FoldImageDemoContent(remember { foldPreviewPainters() }, modifier, initialProgress)
    } else {
        FoldImageNetworkDemo(SingletonImageLoader.get(LocalPlatformContext.current), modifier, initialProgress)
    }
}

@Composable
internal fun FoldImageNetworkDemo(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    initialProgress: Float = 0f,
) {
    val context = LocalPlatformContext.current
    // Keep all eight requests outside the animated drawing subtree. Loading the small
    // demo set up front also makes reversing direction and looping safe on slow networks.
    val painters = FoldDemoPhotoUrls.map { url ->
        val request = remember(context, url) {
            ImageRequest.Builder(context).data(url).size(1600).crossfade(false).build()
        }
        rememberAsyncImagePainter(model = request, imageLoader = imageLoader)
    }
    val states = painters.map { it.state.collectAsState().value }
    if (states.all { it is AsyncImagePainter.State.Success }) {
        FoldImageDemoContent(painters, modifier, initialProgress)
    } else {
        val failed = states.any { it is AsyncImagePainter.State.Error }
        MaterialTheme(colorScheme = darkColorScheme(primary = PreviewAccent, background = PreviewBackground)) {
            Column(modifier.fillMaxSize().background(PreviewBackground).safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                if (failed) {
                    Text("部分图片加载失败，请检查网络后重试。", color = Color.White,
                        modifier = Modifier.testTag("fold-load-error"))
                    Button(onClick = {
                        painters.forEach { if (it.state.value is AsyncImagePainter.State.Error) it.restart() }
                    }, modifier = Modifier.testTag("fold-load-retry")) { Text("重试") }
                } else {
                    CircularProgressIndicator()
                }
                Text("已加载 ${states.count { it is AsyncImagePainter.State.Success }} / ${painters.size} 张图片",
                    color = Color(0xFFE2E5DD), modifier = Modifier.testTag("fold-load-status"))
            }
        }
    }
}

@Composable
internal fun FoldImageDemoContent(
    pictures: List<Painter>,
    modifier: Modifier = Modifier,
    initialProgress: Float = 0f,
) {
    val titles = PhotoTitles
    var index by remember { mutableIntStateOf(0) }
    var direction by remember { mutableStateOf(FoldDirection.Forward) }
    var progress by remember { mutableFloatStateOf(boundedFoldProgress(initialProgress)) }
    var playback by remember { mutableStateOf(FoldPlayback.Paused) }
    var request by remember { mutableIntStateOf(0) }
    var blurEnabled by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    val step = if (direction == FoldDirection.Forward) 1 else -1
    val nextIndex = (index + step + pictures.size) % pictures.size

    LaunchedEffect(playback, request) {
        if (playback == FoldPlayback.Paused) return@LaunchedEffect
        if (playback == FoldPlayback.Rewind) {
            animate(progress, 0f, animationSpec = tween(250)) { value, _ -> progress = value }
            playback = FoldPlayback.Paused
            return@LaunchedEffect
        }
        do {
            animate(progress, 1f, animationSpec = tween(
                durationMillis = (1800 * (1f - progress)).roundToInt().coerceAtLeast(1),
                easing = FastOutSlowInEasing,
            )) { value, _ -> progress = value }
            index = (index + (if (direction == FoldDirection.Forward) 1 else -1) + pictures.size) % pictures.size
            progress = 0f
            if (playback == FoldPlayback.Once) {
                playback = FoldPlayback.Paused
                break
            }
            delay(2200)
        } while (true)
    }

    fun advance(newDirection: FoldDirection) {
        // A manually scrubbed endpoint is already showing the destination image.
        if (progress == 1f) index = nextIndex
        if (direction != newDirection || progress == 1f) progress = 0f
        direction = newDirection
        playback = FoldPlayback.Once
        request++
    }

    BackHandler(enabled = !controlsVisible) {
        controlsVisible = true
        true
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = PreviewAccent, background = PreviewBackground)) {
        val photo: @Composable (Modifier) -> Unit = { photoModifier ->
            FoldImageTransition(
                current = pictures[index],
                next = pictures[nextIndex],
                progress = { progress },
                // Swiping left turns the right-hand flap towards the left, revealing next.
                direction = if (direction == FoldDirection.Forward) FoldDirection.Backward else FoldDirection.Forward,
                blurEnabled = blurEnabled,
                cornerRadius = if (controlsVisible) 24.dp else 0.dp,
                contentDescription = titles[if (progress == 1f) nextIndex else index],
                modifier = photoModifier.testTag("fold-image")
                    .pointerInput(Unit) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                playback = FoldPlayback.Paused
                                distance = progress * size.width * if (direction == FoldDirection.Forward) -1f else 1f
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                distance += amount
                                direction = if (distance <= 0f) FoldDirection.Forward else FoldDirection.Backward
                                progress = (abs(distance) / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                playback = if (progress >= .2f) FoldPlayback.Once else FoldPlayback.Rewind
                                request++
                            },
                            onDragCancel = { playback = FoldPlayback.Rewind; request++ },
                        )
                    },
            )
        }
        val controls: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().testTag("fold-controls")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(titles[if (progress == 1f) nextIndex else index], color = Color(0xFFE2E5DD), fontSize = 13.sp)
                    Text("${(progress * 100).roundToInt()}%", Modifier.testTag("fold-progress-label"),
                        color = PreviewAccent, fontSize = 13.sp)
                }
                Slider(
                    value = progress,
                    onValueChange = { playback = FoldPlayback.Paused; progress = it },
                    modifier = Modifier.fillMaxWidth().testTag("fold-progress")
                        .semantics { contentDescription = "折叠进度" },
                    colors = SliderDefaults.colors(thumbColor = PreviewAccent, activeTrackColor = PreviewAccent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val interior = progress > 0f && progress < 1f
                    OutlinedButton(
                        onClick = { advance(FoldDirection.Backward) },
                        enabled = playback != FoldPlayback.Once && (!interior || direction == FoldDirection.Backward),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).testTag("fold-previous"),
                    ) { Text("上一张") }
                    Button(
                        onClick = { playback = if (playback == FoldPlayback.Paused) FoldPlayback.Loop else FoldPlayback.Paused },
                        colors = ButtonDefaults.buttonColors(containerColor = PreviewAccent, contentColor = PreviewBackground),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).testTag("fold-play"),
                    ) { Text(if (playback == FoldPlayback.Paused) "自动播放" else "暂停") }
                    OutlinedButton(
                        onClick = { advance(FoldDirection.Forward) },
                        enabled = playback != FoldPlayback.Once && (!interior || direction == FoldDirection.Forward),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).testTag("fold-next"),
                    ) { Text("下一张") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = blurEnabled, onCheckedChange = { blurEnabled = it },
                        modifier = Modifier.semantics { contentDescription = "渐变模糊" })
                    Text("渐变模糊", color = Color(0xFFE2E5DD), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        playback = FoldPlayback.Paused
                        if (progress == 1f) index = nextIndex
                        progress = 0f
                        controlsVisible = false
                    }, modifier = Modifier.testTag("fold-fullscreen")) { Text("进入全屏") }
                }
            }
        }
        val heading: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth()) {
                Text("SHOWCASE", color = PreviewAccent,
                    fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text("MOTION DUO", color = Color(0xFFF3F1E9),
                    fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("拖动进度，观察折叠、渐变模糊与光影。",
                    color = Color(0xFF9EA69F), fontSize = 13.sp)
            }
        }
        BoxWithConstraints(modifier.fillMaxSize().background(PreviewBackground).clipToBounds()) {
            if (!controlsVisible) {
                photo(Modifier.fillMaxSize())
            } else if (maxWidth >= 600.dp && maxWidth > maxHeight && maxHeight < 520.dp) {
                // Short landscape windows put controls beside the stage, preserving enough
                // vertical room for the entire perspective-expanded fold to remain visible.
                Row(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val ratio = 16f / 9f
                        val photoWidth = minOf(maxWidth, maxHeight / 1.28f * ratio)
                        Box(Modifier.width(photoWidth).testTag("fold-preview-stage")
                            .padding(vertical = photoWidth / ratio * .14f).align(Alignment.Center)) {
                            photo(Modifier.fillMaxWidth().aspectRatio(ratio))
                        }
                    }
                    Column(Modifier.width(296.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center) {
                        heading()
                        Spacer(Modifier.height(20.dp))
                        controls()
                    }
                }
            } else {
                val horizontalPadding = if (maxWidth < 600.dp) 24.dp else 56.dp
                val ratio = if (maxWidth < 600.dp) 4f / 5f else 16f / 9f
                val photoHeight = ((maxHeight - 350.dp) / 1.28f).coerceAtLeast(160.dp)
                val photoWidth = minOf((maxWidth - horizontalPadding * 2).coerceAtLeast(1.dp),
                    photoHeight * ratio, 960.dp)
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    heading()
                    // Do not clip the photo itself: its top/bottom edges need 12.5% extra
                    // height at the steepest fold. A 14% margin also leaves breathing room.
                    Box(Modifier.width(photoWidth).testTag("fold-preview-stage")
                        .padding(vertical = photoWidth / ratio * .14f)) {
                        photo(Modifier.fillMaxWidth().aspectRatio(ratio))
                    }
                    controls()
                }
            }
        }
    }
}

@Preview(name = "Fold · Interactive", widthDp = 1000, heightDp = 880)
@Composable
fun FoldImageInteractivePreview() = FoldImageDemo()

@Preview(name = "Fold · Closing", widthDp = 1000, heightDp = 880)
@Composable
fun FoldImageClosingPreview() = FoldImageDemo(initialProgress = .28f)

@Preview(name = "Fold · Opening portrait", widthDp = 420, heightDp = 900)
@Composable
fun FoldImageOpeningPreview() = FoldImageDemo(initialProgress = .72f)
