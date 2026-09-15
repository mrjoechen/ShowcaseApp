package com.alpha.showcase.common

import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.ui.play.AnimatedMediaImage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import okio.use
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.use as useSkia

/** GIFs use the existing Coil fetchers, including authenticated and local-file sources. */
internal class SkiaGifDecoder(private val source: ImageSource) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use {
            require(!it.request(MAX_ENCODED_BYTES + 1)) { "GIF exceeds 32 MiB" }
            it.readByteArray()
        }
        val image = Data.makeFromBytes(bytes).useSkia { data ->
            Codec.makeFromData(data).useSkia { codec ->
                val width = codec.imageInfo.width
                val height = codec.imageInfo.height
                // GIF codecs require a full-size compositing buffer even for a small viewport.
                require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
                    "GIF canvas exceeds 16 megapixels"
                }
                val durations = IntArray(codec.frameCount) { frame ->
                    codec.getFrameInfo(frame).duration.let { if (it < 20) 100 else it }
                }
                require(durations.isNotEmpty()) { "GIF has no frames" }
                Bitmap().useSkia { bitmap ->
                    check(bitmap.allocPixels(ImageInfo.makeN32Premul(width, height)))
                    codec.readPixels(bitmap, 0)
                    SkiaGifImage(bytes, Image.makeFromBitmap(bitmap), durations, codec.repetitionCount)
                }
            }
        }
        return DecodeResult(image, isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source()
            // Sniff bytes, not URL suffixes or server MIME types. rangeEquals doesn't consume them.
            return if (source.rangeEquals(0, GIF87A) || source.rangeEquals(0, GIF89A)) {
                SkiaGifDecoder(result.source)
            } else null
        }
    }

    private companion object {
        val GIF87A = "GIF87a".encodeUtf8()
        val GIF89A = "GIF89a".encodeUtf8()
        const val MAX_ENCODED_BYTES = 32L * 1024 * 1024
        const val MAX_PIXELS = 16L * 1024 * 1024
    }
}

internal class SkiaGifImage(
    internal val bytes: ByteArray,
    internal val firstFrame: Image,
    internal val durations: IntArray,
    internal val repetitions: Int,
) : AnimatedMediaImage {
    override val width get() = firstFrame.width
    override val height get() = firstFrame.height
    override val size get() = bytes.size.toLong() + 4L * width * height + 4L * durations.size
    override val shareable = true
    // Non-animated consumers (metadata, AI, thumbnails) get a stable first frame.
    override fun draw(canvas: Canvas) { canvas.drawImage(firstFrame, 0f, 0f) }
    override fun painter(scope: CoroutineScope, isPlaying: () -> Boolean): Painter =
        SkiaGifPainter(this, scope, isPlaying)
}

internal class SkiaGifPainter(
    private val image: SkiaGifImage,
    private val scope: CoroutineScope,
    private val isPlaying: () -> Boolean,
) : Painter(), RememberObserver {
    private var job: Job? = null
    private var frame: Image? by mutableStateOf(null)
    override val intrinsicSize = Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        scale(size.width / image.width, size.height / image.height, Offset.Zero) {
            drawContext.canvas.skiaCanvas.drawImage(frame ?: image.firstFrame, 0f, 0f)
        }
    }

    override fun onRemembered() {
        if (job != null || image.durations.size <= 1) return
        job = scope.launch {
            try {
                Data.makeFromBytes(image.bytes).useSkia { data ->
                    Codec.makeFromData(data).useSkia { codec ->
                        // Skia requires parsing the frame table before getFrameInfo is valid.
                        check(codec.frameCount == image.durations.size)
                        Bitmap().useSkia { bitmap ->
                            check(bitmap.allocPixels(ImageInfo.makeN32Premul(image.width, image.height)))
                            var index = 0
                            var completedRepeats = 0
                            var priorFrame = -1
                            var decodedIndex = -1
                            var finished = false
                            snapshotFlow(isPlaying).collectLatest { playing ->
                                if (!playing || finished) return@collectLatest
                                while (true) {
                                    if (decodedIndex != index) {
                                        codec.readPixels(bitmap, index, priorFrame)
                                        bitmap.notifyPixelsChanged()
                                        val next = Image.makeFromBitmap(bitmap)
                                        val previous = frame
                                        frame = next
                                        previous?.close()
                                        priorFrame = if (codec.getFrameInfo(index).disposalMethod ==
                                            AnimationDisposalMode.RESTORE_PREVIOUS) -1 else index
                                        decodedIndex = index
                                    }
                                    val started = withFrameNanos { it }
                                    val duration = image.durations[index] * 1_000_000L
                                    do {
                                        val elapsed = withFrameNanos { it } - started
                                    } while (elapsed < duration)
                                    if (index == image.durations.lastIndex) {
                                        if (image.repetitions >= 0 && completedRepeats >= image.repetitions) {
                                            finished = true
                                            break
                                        }
                                        completedRepeats++
                                        index = 0
                                        priorFrame = -1
                                    } else index++
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A damaged later frame must not cancel the parent composition scope.
                // Keep the last successfully decoded frame visible.
                Napier.w("GIF playback stopped at a damaged frame", e)
            }
        }
    }

    override fun onForgotten() {
        job?.cancel()
        job = null
        val previous = frame
        frame = null
        previous?.close()
    }

    override fun onAbandoned() = onForgotten()
}
