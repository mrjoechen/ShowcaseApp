@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.util.component1
import coil3.util.component2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import openize.heic.decoder.HeicImage
import openize.heic.decoder.PixelFormat
import openize.io.IOFileStream
import openize.io.IOMode
import openize.io.IOStream
import openize.isobmff.CleanApertureBox
import openize.isobmff.ImageRotation
import openize.isobmff.ImageMirror
import openize.isobmff.ItemPropertyContainerBox
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Rect

/** Desktop-only HEVC/HEIF decoding; all fetching, authentication and caching remain in Coil. */
internal class DesktopHeicDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        // Large iPhone grids need full-resolution working buffers even for small requests.
        // Limit simultaneous HEIC decodes independently of ordinary images and GIFs.
        decodeSlots.withPermit {
            ensureActive()
            // Coil owns this file and deletes it if it had to materialize a streaming source.
            val file = source.file()
            require((source.fileSystem.metadata(file).size ?: Long.MAX_VALUE) <= MAX_FILE_BYTES) {
                "HEIC file exceeds 64 MiB"
            }
            IOFileStream(file.toString(), IOMode.READ).use { stream ->
                val input = HeicBufferedInput(stream)
                val (heic, rotateHalfTurn) = input.loadImage()
                val width = heic.width
                val height = heic.height
                require(width in 1..MAX_PIXELS && height in 1..MAX_PIXELS && width * height <= MAX_PIXELS) {
                    "HEIC image exceeds 64 megapixels"
                }
                // The library resolves the primary item, grid, mirror and rotation.
                // Decode display pixels, rather than a depth map, thumbnail or HDR gain map.
                val pixels = checkNotNull(heic.getByteArray(PixelFormat.Rgba32)) { "HEIC has no display pixels" }
                ensureActive()
                val info = ImageInfo(width.toInt(), height.toInt(), ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
                Image.makeRaster(info, pixels, width.toInt() * 4).use { image ->
                    val bounds = heic.displayBounds()
                    val bitmap = sampledBitmap(image, bounds, options, rotateHalfTurn)
                    bitmap.setImmutable()
                    DecodeResult(bitmap.asImage(), bitmap.width < bounds.width || bitmap.height < bounds.height)
                }
            }
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isHeic(result.source.source())) DesktopHeicDecoder(result.source, options) else null
    }

    companion object {
        private val decodeSlots = Semaphore(1)
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private const val MAX_PIXELS = 64L * 1024 * 1024
        private val FTYP = "ftyp".encodeUtf8()
        private val HEVC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis")

        internal fun isHeic(source: BufferedSource): Boolean {
            if (!source.request(16) || !source.rangeEquals(4, FTYP)) return false
            val header = source.peek()
            var boxSize = header.readInt().toLong() and 0xffffffffL
            header.skip(4)
            val headerSize = if (boxSize == 1L) {
                boxSize = header.readLong()
                16L
            } else 8L
            // Bound lookahead and only accept HEVC brands. mif1 alone also includes AVIF.
            if (boxSize !in (headerSize + 8)..4096 || (boxSize - headerSize) % 4 != 0L || !source.request(boxSize)) return false
            if (header.readUtf8(4) in HEVC_BRANDS) return true
            header.skip(4) // minor version
            repeat(((boxSize - headerSize - 8) / 4).toInt()) {
                if (header.readUtf8(4) in HEVC_BRANDS) return true
            }
            return false
        }
    }
}

/**
 * Openize 26.5's BitStreamReader.moreData checks the underlying stream's EOF even
 * when its 4096-byte buffer still contains unread boxes. A virtual ISO BMFF `free`
 * box keeps that lookahead away from EOF without changing source bytes or offsets.
 * This also covers a meta box at the end of a large file, not just tiny images.
 */
private class HeicBufferedInput(private val stream: IOStream) : IOStream by stream {
    private val originalLength = stream.length
    private val majorBrandOffset = ByteArray(4).let {
        stream.read(it)
        stream.setPosition(0)
        if (it.contentEquals(byteArrayOf(0, 0, 0, 1))) 16L else 8L
    }
    private val trailer = ByteArray(4096).apply {
        this[2] = 0x10 // big-endian box size: 4096
        "free".encodeToByteArray().copyInto(this, 4)
    }
    private var halfTurnOffset: Long? = null

    fun loadImage(): Pair<HeicImage, Boolean> {
        // Validate against the real EOF before introducing virtual lookahead bytes.
        // A truncated mdat must not be silently completed by the free-box trailer.
        boxes(0, originalLength).forEach { }
        stream.setPosition(0)
        val image = HeicImage.load(this)
        val properties = image.header.meta.getiprp()
        val halfTurn = properties.properties[image.header.defaultFrameId].orEmpty()
            .filterIsInstance<ImageRotation>().firstOrNull { it.angle.toInt() == 2 }
            ?: return image to false
        // Openize 26.5 uses width for both axes in its 180-degree transform.
        // Suppress only the primary item's half-turn in this read-only view, then
        // apply it with Skia after decoding. Other items and the source stay intact.
        val index = properties.children.filterIsInstance<ItemPropertyContainerBox>()
            .single().items.entries.single { it.value === halfTurn }.key
        val meta = boxes(0, originalLength).first { it.type == "meta" }
        val iprp = boxes(meta.payload + 4, meta.end).first { it.type == "iprp" }
        val ipco = boxes(iprp.payload, iprp.end).first { it.type == "ipco" }
        val rotation = boxes(ipco.payload, ipco.end).elementAt(index - 1)
        check(rotation.type == "irot") { "Invalid HEIC rotation property" }
        halfTurnOffset = rotation.payload
        stream.setPosition(0)
        return HeicImage.load(this) to true
    }

    private data class BoxLocation(val type: String, val payload: Long, val end: Long)

    private fun boxes(start: Long, end: Long): Sequence<BoxLocation> = sequence {
        var offset = start
        while (offset < end) {
            require(end - offset >= 8) { "Truncated HEIC box" }
            stream.setPosition(offset)
            val header = ByteArray(8)
            check(stream.read(header) == header.size)
            val bytes = java.nio.ByteBuffer.wrap(header)
            var size = bytes.int.toLong() and 0xffffffffL
            val type = header.decodeToString(4, 8)
            var headerSize = 8L
            if (size == 1L) {
                require(end - offset >= 16)
                check(stream.read(header) == header.size)
                size = java.nio.ByteBuffer.wrap(header).long
                headerSize = 16L
            } else if (size == 0L) size = end - offset
            require(size >= headerSize && size <= end - offset) { "Invalid HEIC box length" }
            yield(BoxLocation(type, offset + headerSize, offset + size))
            offset += size
        }
    }

    override fun getLength(): Long = originalLength + trailer.size

    override fun read(dst: ByteArray): Int = read(dst, 0, dst.size)

    override fun read(dst: ByteArray, offset: Int, count: Int): Int {
        require(offset >= 0 && count >= 0 && offset <= dst.size - count)
        if (count == 0) return 0
        val position = stream.position
        check(position in 0 until length) { "Unexpected end of HEIC data" }
        val available = minOf(count.toLong(), length - position).toInt()
        val fileBytes = minOf(available.toLong(), (originalLength - position).coerceAtLeast(0)).toInt()
        var copied = 0
        while (copied < fileBytes) {
            val read = stream.read(dst, offset + copied, fileBytes - copied)
            check(read > 0) { "Unexpected end of HEIC data" }
            copied += read
        }
        if (copied < available) {
            val trailerOffset = (position + copied - originalLength).toInt()
            trailer.copyInto(dst, offset + copied, trailerOffset, trailerOffset + available - copied)
            stream.setPosition(position + available)
        }
        // canLoad only recognizes `heic`; HEVC Main 10 files often advertise `heix`.
        // The factory already validated a HEVC brand. The codec reads bit depth from
        // hvcC/SPS, so normalize only this probe's brand, never the actual source.
        for (index in 0..3) {
            val relative = majorBrandOffset + index - position
            if (relative in 0 until available.toLong()) dst[offset + relative.toInt()] = "heic"[index].code.toByte()
        }
        halfTurnOffset?.let {
            val relative = it - position
            if (relative in 0 until available.toLong()) dst[offset + relative.toInt()] = 0
        }
        return available
    }
}

// Openize exposes clap metadata but does not apply the clean aperture itself.
private fun HeicImage.displayBounds(): Rect {
    val properties = header.meta.getiprp().properties[header.defaultFrameId].orEmpty()
    val clap = properties.filterIsInstance<CleanApertureBox>().lastOrNull()
        ?: return Rect.makeWH(width.toFloat(), height.toFloat())
    val rotation = properties.filterIsInstance<ImageRotation>().lastOrNull()?.angle?.toInt() ?: 0
    var fullWidth = (if (rotation % 2 == 0) width else height).toFloat()
    var fullHeight = (if (rotation % 2 == 0) height else width).toFloat()
    require(clap.cleanApertureWidthD > 0 && clap.cleanApertureHeightD > 0 && clap.horizOffD > 0 && clap.vertOffD > 0)
    var cropWidth = clap.cleanApertureWidthN.toFloat() / clap.cleanApertureWidthD
    var cropHeight = clap.cleanApertureHeightN.toFloat() / clap.cleanApertureHeightD
    var left = (fullWidth - cropWidth) / 2 + clap.horizOffN.toFloat() / clap.horizOffD
    var top = (fullHeight - cropHeight) / 2 + clap.vertOffN.toFloat() / clap.vertOffD
    require(cropWidth > 0 && cropHeight > 0 && left >= 0 && top >= 0 &&
        left + cropWidth <= fullWidth && top + cropHeight <= fullHeight) { "Invalid HEIC clean aperture" }
    repeat(rotation) {
        val nextTop = fullWidth - left - cropWidth
        left = top
        top = nextTop
        fullWidth = fullHeight.also { fullHeight = fullWidth }
        cropWidth = cropHeight.also { cropHeight = cropWidth }
    }
    when (properties.filterIsInstance<ImageMirror>().lastOrNull()?.axis?.toInt()) {
        0 -> top = fullHeight - top - cropHeight
        1 -> left = fullWidth - left - cropWidth
    }
    return Rect.makeXYWH(left, top, cropWidth, cropHeight)
}

private fun sampledBitmap(image: Image, bounds: Rect, options: Options, rotateHalfTurn: Boolean): Bitmap {
    val sourceWidth = bounds.width.toInt().coerceAtLeast(1)
    val sourceHeight = bounds.height.toInt().coerceAtLeast(1)
    val (targetWidth, targetHeight) = DecodeUtils.computeDstSize(
        sourceWidth, sourceHeight, options.size, options.scale, options.maxBitmapSize,
    )
    var ratio = DecodeUtils.computeSizeMultiplier(
        sourceWidth, sourceHeight, targetWidth, targetHeight, options.scale, options.maxBitmapSize,
    )
    if (options.precision == Precision.INEXACT) ratio = ratio.coerceAtMost(1.0)
    val width = (sourceWidth * ratio).toInt().coerceAtLeast(1)
    val height = (sourceHeight * ratio).toInt().coerceAtLeast(1)
    val bitmap = Bitmap()
    try {
        check(bitmap.allocN32Pixels(width, height)) { "Unable to allocate HEIC output" }
        Canvas(bitmap).use { canvas ->
            if (rotateHalfTurn) {
                canvas.translate(width.toFloat(), height.toFloat())
                canvas.rotate(180f)
            }
            canvas.drawImageRect(image, bounds.left, bounds.top, bounds.right, bounds.bottom,
                0f, 0f, width.toFloat(), height.toFloat(), SamplingMode.DEFAULT, null, false)
        }
        return bitmap
    } catch (e: Throwable) {
        bitmap.close()
        throw e
    }
}
