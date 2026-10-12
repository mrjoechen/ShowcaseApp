@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import coil3.Image
import com.alpha.showcase.common.ai.ImageContentIdentity
import com.alpha.showcase.common.ai.decodeWithImageIdentity
import com.alpha.showcase.common.ai.completeImageNetworkFetcherFactory
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
        focalLength?.takeIf { it.isFinite() && it > 0 }?.let(::formatExifFocalLength),
    ).joinToString(" · ")
    if (exposure.isNotBlank()) row(MediaMetadataKind.Exposure, exposure)
    orientedSize?.let { row(MediaMetadataKind.Dimensions, formatMediaDimensions(it.width, it.height)) }
    locationShown?.displayString?.let { row(MediaMetadataKind.Location, it) }
    gpsCoordinates?.takeIf { it.isValid() }?.let { row(MediaMetadataKind.Location, "GPS: " + it.latLongString) }
    filmSimulation?.takeIf { it.isNotBlank() }?.let { row(MediaMetadataKind.Film, it) }
    addAll(additionalRows.map { it.copy(text = it.text.take(1024)) })
    description?.takeIf { it.isNotBlank() && it != title }?.let { row(MediaMetadataKind.Description, it) }
}.distinct(), hasDimensions = orientedSize != null,
    coordinates = gpsCoordinates?.takeIf { it.isValid() }?.let { PhotoCoordinates(it.latitude, it.longitude) })

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

internal actual fun readSourceMetadata(source: BufferedSource, sourceLength: Long?): MediaMetadata? =
    readMediaMetadata { MetadataByteReader(source, sourceLength) }
