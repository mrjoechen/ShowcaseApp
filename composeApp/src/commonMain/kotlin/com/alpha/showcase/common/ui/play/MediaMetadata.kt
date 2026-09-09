@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import coil3.Image
import coil3.toUri
import com.alpha.showcase.common.networkfile.model.NetworkFile
import de.stefan_oltmann.kim.format.tiff.constant.ExifTag
import de.stefan_oltmann.kim.format.tiff.constant.TiffTag
import kotlin.math.roundToLong
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ImageResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.common.convertToSummary
import de.stefan_oltmann.kim.common.KimValueFormatter
import de.stefan_oltmann.kim.input.ByteReader
import de.stefan_oltmann.kim.model.MetadataSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.BufferedSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Small display model: do not keep thumbnails, face records or raw image bytes in the cache. */
internal enum class MediaMetadataKind {
    FileName, Date, Camera, Lens, Exposure, Dimensions, FileSize, Location, Description, Film, Author, Copyright,
}

internal data class MediaMetadataEntry(val kind: MediaMetadataKind, val text: String)

internal data class MediaMetadata(
    val rows: List<MediaMetadataEntry>,
    val hasDimensions: Boolean = false,
    val fileSize: Long? = null,
) {
    val lines: List<String> get() = rows.map { it.text }
}

internal fun MetadataSummary.toMediaMetadata(
    captureTime: String? = null,
    additionalRows: List<MediaMetadataEntry> = emptyList(),
): MediaMetadata = MediaMetadata(buildList {
    fun row(kind: MediaMetadataKind, text: String) { add(MediaMetadataEntry(kind, text.take(1024))) }
    title?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Description, it) }
    (captureTime ?: takenDate?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace('T', ' ')
    })?.let { row(MediaMetadataKind.Date, it) }
    cameraName?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Camera, it) }
    lensName?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Lens, it) }
    val exposure = listOfNotNull(
        fNumber?.takeIf { it.isFinite() && it > 0 }?.let(KimValueFormatter::formatFNumber),
        exposureTime?.takeIf { it.isFinite() && it > 0 }?.let(KimValueFormatter::formatExposureTime),
        iso?.takeIf { it > 0 }?.let(KimValueFormatter::formatIso),
        focalLength?.takeIf { it.isFinite() && it > 0 }?.let(KimValueFormatter::formatFocalLength),
    ).joinToString(" · ")
    if (exposure.isNotBlank()) row(MediaMetadataKind.Exposure, exposure)
    orientedSize?.let { row(MediaMetadataKind.Dimensions, formatMediaDimensions(it.width, it.height)) }
    locationShown?.displayString?.let { row(MediaMetadataKind.Location, it) }
    gpsCoordinates?.takeIf { it.isValid() }?.let { row(MediaMetadataKind.Location, "GPS: " + it.latLongString) }
    filmSimulation?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Film, it) }
    addAll(additionalRows.map { it.copy(text = it.text.take(1024)) })
    description?.takeIf { it.isNotBlank() && it != title }?.let { row(MediaMetadataKind.Description, it) }
}.distinct(), hasDimensions = orientedSize != null)

internal fun formatMediaDimensions(width: Int, height: Int): String =
    "$width × $height" + if (width.toLong() * height >= 100_000) {
        " · ${decimal(width.toDouble() * height / 1_000_000)} MP"
    } else ""

private fun decimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

internal fun formatMediaFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${decimal(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576 -> "${decimal(bytes / 1_048_576.0)} MB"
    bytes >= 1_024 -> "${decimal(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

private fun mediaSource(data: Any): Any = when (data) {
    is DataWithType -> mediaSource(data.data)
    is ResolvedImageModel -> data.stableKey
    else -> data
}

/** Use display fields, never an object's toString() or an authenticated URL's query. */
internal fun mediaMetadataLines(state: MediaItemState): List<String> = mediaMetadataRows(state).map { it.text }

internal fun mediaMetadataRows(state: MediaItemState): List<MediaMetadataEntry> = buildList {
    fun row(kind: MediaMetadataKind, text: String) { add(MediaMetadataEntry(kind, text.take(1024))) }
    val data = mediaSource(state.data)
    val path = when (data) {
        is NetworkFile -> data.fileName
        is UrlWithAuth -> data.url.toUri().path
        is String -> if (data.contains("://")) data.toUri().path else data.substringBefore('?').substringBefore('#')
        else -> null
    }
    path?.replace('\\', '/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.FileName, it) }
    val metadata = state.metadata
    addAll(metadata?.rows.orEmpty())
    if (metadata?.hasDimensions != true) {
        state.displayedImage?.let { row(MediaMetadataKind.Dimensions, formatMediaDimensions(it.width, it.height)) }
    }
    val size = metadata?.fileSize ?: when (data) {
        is NetworkFile -> data.size
        is ByteArray -> data.size.toLong()
        else -> null
    }
    size?.takeIf { it > 0 }?.let { row(MediaMetadataKind.FileSize, formatMediaFileSize(it)) }
    if (metadata == null && data is NetworkFile) {
        data.modTime.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Date, it) }
    }
}

private val metadataEnabled = Extras.Key(false)
private val metadataResult = Extras.Key<MediaMetadata?>(null)
private const val METADATA_EXTRA = "showcase#media_metadata"
private const val METADATA_READY_EXTRA = "showcase#media_metadata_ready"

/** Separate playback entries from thumbnail entries that were decoded without metadata. */
internal fun ImageRequest.Builder.withMediaMetadata() = apply {
    extras[metadataEnabled] = true
    memoryCacheKeyExtra(METADATA_EXTRA, "1")
}

internal val SuccessResult.mediaMetadata: MediaMetadata? get() = request.extras[metadataResult]

/** Install metadata handling together with the cache whose image entries own its lifetime. */
internal fun ImageLoader.Builder.mediaMetadataCache(
    cache: MemoryCache,
    registerComponents: coil3.ComponentRegistry.Builder.() -> Unit = {},
) = apply {
    val decorated = MetadataMemoryCache(cache)
    memoryCache(decorated)
    // Coil's components blocks replace each other. Register application components together.
    components {
        registerComponents()
        add(MediaMetadataInterceptor(decorated))
    }
}

/** No address-keyed side cache: different cached versions of a URL retain their own metadata. */
internal class MetadataMemoryCache(private val delegate: MemoryCache) : MemoryCache by delegate {
    private val lock = SynchronizedObject()
    override fun get(key: MemoryCache.Key): MemoryCache.Value? = synchronized(lock) {
        val entry = delegate[key]
        // Coil stores pixels before its interceptor returns to attach metadata. A
        // concurrent reader must miss until both are published, even for null metadata.
        entry?.takeIf {
            key.extras[METADATA_EXTRA] != "1" || it.extras[METADATA_READY_EXTRA] == true
        }
    }
    override fun set(key: MemoryCache.Key, value: MemoryCache.Value) = synchronized(lock) {
        delegate[key] = value
    }
    override fun remove(key: MemoryCache.Key): Boolean = synchronized(lock) { delegate.remove(key) }
    override fun clear() = synchronized(lock) { delegate.clear() }
    override fun trimToSize(size: Long) = synchronized(lock) { delegate.trimToSize(size) }

    fun metadata(key: MemoryCache.Key?, image: Image): MediaMetadata? = synchronized(lock) {
        key?.let { delegate[it] }?.takeIf { it.image === image }?.extras?.get(METADATA_EXTRA) as? MediaMetadata
    }

    fun attach(key: MemoryCache.Key?, image: Image, metadata: MediaMetadata?) = synchronized(lock) {
        if (key != null) {
            val entry = delegate[key]
            // A concurrent request may already have replaced this entry. Never restore old pixels.
            if (entry?.image === image) {
                val extras = entry.extras.toMutableMap().apply {
                    remove(METADATA_EXTRA)
                    if (metadata != null) put(METADATA_EXTRA, metadata)
                    put(METADATA_READY_EXTRA, true)
                }
                delegate[key] = entry.copy(extras = extras)
            }
        }
    }
}

private class MediaMetadataInterceptor(private val cache: MetadataMemoryCache) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (request.extras[metadataEnabled] != true) return chain.proceed()
        // Per execution, not per remembered request: prefetch and visible loads may run concurrently.
        val decoder = MediaMetadataDecoderFactory(request.decoderFactory, (request.data as? ByteArray)?.size?.toLong())
        val result = chain.withRequest(request.newBuilder().decoderFactory(decoder).build()).proceed()
        if (result !is SuccessResult) return result
        val metadata = if (result.dataSource == DataSource.MEMORY_CACHE) {
            cache.metadata(result.memoryCacheKey, result.image)
        } else {
            decoder.metadata.also { cache.attach(result.memoryCacheKey, result.image, it) }
        }
        return result.copy(request = request.newBuilder().apply { extras[metadataResult] = metadata }.build())
    }
}

internal fun readMediaMetadata(reader: () -> ByteReader): MediaMetadata? {
    return try {
        val byteReader = reader()
        try {
            val raw = Kim.readMetadata(byteReader) ?: return null
            // EXIF is a camera-local wall time. Preserve its offset when present; do not
            // reinterpret it using the viewer's timezone (KIM summary omits OffsetTimeOriginal).
            val captureTime = raw.findStringValue(ExifTag.EXIF_TAG_DATE_TIME_ORIGINAL)
                ?.trim()?.takeIf { value -> value.matches(Regex("\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}")) && !value.startsWith("0000") }
                ?.let { value ->
                    val date = value.take(10).replace(':', '-') + value.drop(10)
                    val offset = raw.findStringValue(ExifTag.EXIF_TAG_OFFSET_TIME_ORIGINAL)?.trim()
                        ?.takeIf { zone -> zone.matches(Regex("[+-]\\d{2}:\\d{2}")) }
                    if (offset != null) "$date $offset" else date
                }
            val details = buildList {
                raw.findShortValue(ExifTag.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT)?.toInt()?.takeIf { value -> value > 0 }
                    ?.let { value -> add(MediaMetadataEntry(MediaMetadataKind.Exposure, "35mm: $value mm")) }
                raw.findStringValue(TiffTag.TIFF_TAG_ARTIST)?.takeIf(String::isNotBlank)?.let { add(MediaMetadataEntry(MediaMetadataKind.Author, it)) }
                raw.findStringValue(TiffTag.TIFF_TAG_COPYRIGHT)?.takeIf(String::isNotBlank)?.let { add(MediaMetadataEntry(MediaMetadataKind.Copyright, it)) }
            }
            raw.convertToSummary().toMediaMetadata(captureTime, details)
        } finally {
            byteReader.close()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Missing, malformed or oversized metadata must never prevent image decoding.
        null
    }
}

/** Peek at Coil's existing source before its normal decoder; no second fetch or bitmap conversion. */
private class MediaMetadataDecoderFactory(
    private val original: Decoder.Factory?,
    private val sourceLength: Long?,
) : Decoder.Factory {
    private val resultMetadata = MutableStateFlow<MediaMetadata?>(null)
    val metadata: MediaMetadata? get() = resultMetadata.value
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        return object : Decoder {
            override suspend fun decode(): coil3.decode.DecodeResult? {
                val fileSize = sourceLength ?: try {
                    result.source.fileOrNull()?.let { path -> result.source.fileSystem.metadataOrNull(path)?.size }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
                val metadata = readMediaMetadata { MetadataByteReader(result.source.source().peek(), fileSize) }
                resultMetadata.value = metadata?.copy(fileSize = fileSize)
                // Android's StaticImageDecoder factory seeks the content URI's shared
                // file descriptor. Read EXIF first, before that invalidates the position
                // expected by Okio's buffered source (especially for multi-segment EXIF).
                val decoder = original?.create(result, options, imageLoader)
                    ?: imageLoader.components.newDecoder(result, options, imageLoader)?.first
                return decoder?.decode()
            }
        }
    }
}

/** Bound retained peek bytes and malformed tag allocations while allowing streaming header reads. */
internal class MetadataByteReader(private val source: BufferedSource, sourceLength: Long? = null) : ByteReader {
    // KIM's JPEG parser treats negative lengths as EOF, not as an unknown length.
    // Expose the bounded readable window; the source still enforces the actual EOF.
    // This permits streaming headers without buffering the entire image first.
    override val contentLength: Long = sourceLength?.takeIf { it >= 0 }?.coerceAtMost(4 * 1024 * 1024L)
        ?: (4 * 1024 * 1024L)
    private var remaining = contentLength.toInt()
    override fun readByte(): Byte? {
        if (source.exhausted()) return null
        check(remaining > 0) { "Metadata read limit" }
        remaining--
        return source.readByte()
    }
    override fun readBytes(count: Int): ByteArray {
        require(count in 0..remaining) { "Metadata read limit" }
        remaining -= count
        return source.readByteArray(count.toLong())
    }
    override fun close() = source.close()
}
