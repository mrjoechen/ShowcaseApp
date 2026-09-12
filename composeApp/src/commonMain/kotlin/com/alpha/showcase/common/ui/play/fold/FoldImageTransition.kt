package com.alpha.showcase.common.ui.play.fold

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// Neighbouring blur levels are blended by source-image position, not by screen-space stripes.
// This portable approximation uses native BlurEffect on Android 12+ and the Skia platforms.
// Android < 12 retains the folding geometry and shading but renders without blur.
private val BlurLevels = listOf(.0625f, .125f, .25f, .5f, 1f)

/**
 * A controlled photo transition: 0 shows [current], 1 shows [next].
 *
 * Drive [progress] with an Animatable, pager offset, or a slider. At completion the caller can
 * promote next to current and reset progress to 0 in the same state update. Supply loaded painters
 * (for example Coil's successful painter) so a network load cannot leave the turning flap empty.
 * Give this component a size/aspect ratio. It may draw a small amount above/below its bounds during
 * the fold; leave breathing room or clip at the outer presentation container if desired.
 *
 * The image stays in its original front projection as the flap clips it, matching the reference's
 * projected-screen appearance. This is a flat photo fold, without a phone model or curved hinge.
 */
@Composable
fun FoldImageTransition(
    current: Painter,
    next: Painter,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    direction: FoldDirection = FoldDirection.Forward,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    blurEnabled: Boolean = true,
    cornerRadius: Dp = 0.dp,
) {
    val latestProgress = rememberUpdatedState(progress)
    val opening by remember { derivedStateOf { boundedFoldProgress(latestProgress.value()) >= .5f } }
    val currentLayer = rememberGraphicsLayer()
    val nextLayer = rememberGraphicsLayer()
    val blurredLayers = BlurLevels.map { rememberGraphicsLayer() }

    Box(modifier
        .semantics { contentDescription?.let { this.contentDescription = it } }
        .drawWithCache {
            val layerSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            // Flatten the photo and its black letterbox backing before either half is clipped.
            // A display-list-only layer would apply edge coverage to those two draws separately.
            currentLayer.compositingStrategy = CompositingStrategy.Offscreen
            nextLayer.compositingStrategy = CompositingStrategy.Offscreen
            currentLayer.record(size = layerSize) { drawPhoto(current, contentScale) }
            nextLayer.record(size = layerSize) { drawPhoto(next, contentScale) }
            val movingLayer = if (opening) nextLayer else currentLayer
            // 1.75x the reference's 72px radius, scaled with the rendered image.
            val maxRadius = (size.width * 126f / 2670f).coerceAtMost(56.dp.toPx())
            if (blurEnabled && maxRadius > 0f) {
                blurredLayers.forEachIndexed { index, layer ->
                    layer.renderEffect = BlurEffect(maxRadius * BlurLevels[index], maxRadius * BlurLevels[index], TileMode.Decal)
                    layer.record(size = layerSize) { drawLayer(movingLayer) }
                }
            }
            val bounds = Rect(Offset.Zero, size)
            val compositePaint = Paint()
            val flap = Path()
            val center = size.width / 2f
            val radius = cornerRadius.toPx().coerceIn(0f, minOf(size.width, size.height) / 2f)
            val outline = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius))) }

            onDrawBehind {
                if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                val p = boundedFoldProgress(latestProgress.value())
                if (p == 0f || p == 1f) {
                    clipPath(outline) { drawLayer(if (p == 0f) currentLayer else nextLayer) }
                    return@onDrawBehind
                }
                val frame = foldFrame(p, direction, size.width, size.height)
                val forward = direction == FoldDirection.Forward
                // These two stationary halves never switch at the midpoint. The moving face
                // changes only when it has zero visible width, preventing a half-image flash.
                // Start with a complete background, avoiding a gap between two fractional clips.
                clipPath(outline) {
                    drawContext.canvas.saveLayer(bounds, compositePaint)
                    drawLayer(nextLayer)
                    if (forward) clipRect(left = center) { drawLayer(currentLayer) }
                    else clipRect(right = center) { drawLayer(currentLayer) }
                    drawContext.canvas.restore()
                }
                if (abs(frame.outerX - center) < .25f) return@onDrawBehind

                // Overlap the matching stationary face by one physical pixel. Its identical
                // image covers the antialiased hinge edge, including on odd-width surfaces.
                val hingeX = center + if (frame.movingLeft) 1f else -1f
                flap.setFoldOutline(frame, hingeX, size.height, radius)
                clipPath(flap) {
                    // Resolve the antialiased polygon once when restoring the whole face.
                    // Applying its fractional coverage separately to each DstIn blur mask
                    // otherwise leaves a dark fringe even with an identical photo underneath.
                    drawContext.canvas.saveLayer(
                        Rect(0f, frame.outerTop, size.width, size.height - frame.outerTop),
                        compositePaint,
                    )
                    // The photo layer is already opaque. Painting black underneath it inside
                    // an antialiased clip would leave a dark fringe from double edge coverage.
                    // Only the perspective margins outside the image need a black backing.
                    if (frame.outerTop < 0f) {
                        val margin = androidx.compose.ui.geometry.Size(size.width, -frame.outerTop)
                        drawRect(Color.Black, topLeft = Offset(0f, frame.outerTop), size = margin)
                        drawRect(Color.Black, topLeft = Offset(0f, size.height), size = margin)
                    }
                    drawLayer(movingLayer)
                    if (blurEnabled && maxRadius > 0f) {
                        // A cumulative mask completely covers earlier levels up to the lower
                        // radius, then crossfades only the two neighbouring levels. There are
                        // no repeated image loads, CPU readbacks, or per-frame bitmap captures.
                        blurredLayers.forEachIndexed { index, layer ->
                            val lower = if (index == 0) 0f else BlurLevels[index - 1]
                            val upper = BlurLevels[index]
                            if (frame.motion <= lower) return@forEachIndexed
                            val maskStops = (0..32).map { step ->
                                val position = step / 32f
                                val edge = if (frame.movingLeft) 1f - position else position
                                val radius = frame.motion * edge.pow(1.15f)
                                position to Color.White.copy(alpha = ((radius - lower) / (upper - lower)).coerceIn(0f, 1f))
                            }.toTypedArray()
                            val start = if (frame.movingLeft) 0f else center
                            val end = if (frame.movingLeft) center else size.width
                            drawContext.canvas.saveLayer(bounds, compositePaint)
                            drawLayer(layer)
                            drawRect(Brush.horizontalGradient(*maskStops, startX = start, endX = end), blendMode = BlendMode.DstIn)
                            drawContext.canvas.restore()
                        }
                    }
                    val shadeStops = (0..24).map { step ->
                        val position = step / 24f
                        val edge = if (frame.movingLeft) 1f - position else position
                        val dark = ((edge - .2f) / .8f).coerceIn(0f, 1f).pow(1.35f)
                        position to Color.Black.copy(alpha = (2f * frame.motion * dark).coerceIn(0f, 1f))
                    }.toTypedArray()
                    drawRect(Brush.horizontalGradient(*shadeStops,
                        startX = if (frame.movingLeft) 0f else center,
                        endX = if (frame.movingLeft) center else size.width))
                    drawContext.canvas.restore()
                }
            }
        })
}

/** Only round the two outside corners: rounding the hinge would open a hole in the image. */
private fun Path.setFoldOutline(frame: FoldFrame, hingeX: Float, height: Float, radius: Float) {
    reset()
    moveTo(hingeX, 0f)
    val r = minOf(radius, abs(frame.outerX - hingeX) * .45f, (height - 2f * frame.outerTop) / 2f)
    if (r > 0f) {
        val top = Offset(frame.outerX, frame.outerTop)
        val bottom = Offset(frame.outerX, height - frame.outerTop)
        val topEdge = Offset(hingeX, 0f) - top
        val bottomEdge = Offset(hingeX, height) - bottom
        val start = top + topEdge * (r / topEdge.getDistance())
        val topEnd = top + Offset(0f, r)
        val bottomStart = bottom - Offset(0f, r)
        val end = bottom + bottomEdge * (r / bottomEdge.getDistance())
        // A quarter-circle cubic when flat, following the projected edge tangents when folded.
        val k = .55228475f
        val a = start + (top - start) * k
        val b = topEnd + (top - topEnd) * k
        val c = bottomStart + (bottom - bottomStart) * k
        val d = end + (bottom - end) * k
        lineTo(start.x, start.y)
        cubicTo(a.x, a.y, b.x, b.y, topEnd.x, topEnd.y)
        lineTo(bottomStart.x, bottomStart.y)
        cubicTo(c.x, c.y, d.x, d.y, end.x, end.y)
    } else {
        lineTo(frame.outerX, frame.outerTop)
        lineTo(frame.outerX, height - frame.outerTop)
    }
    lineTo(hingeX, height)
    close()
}

private fun DrawScope.drawPhoto(painter: Painter, contentScale: ContentScale) {
    drawRect(Color.Black)
    val intrinsic = painter.intrinsicSize
    val source = if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0 && intrinsic.height > 0) intrinsic else size
    val scale = contentScale.computeScaleFactor(source, size)
    val destination = androidx.compose.ui.geometry.Size(source.width * scale.scaleX, source.height * scale.scaleY)
    clipRect {
        translate((size.width - destination.width) / 2f, (size.height - destination.height) / 2f) {
            with(painter) { draw(destination) }
        }
    }
}
