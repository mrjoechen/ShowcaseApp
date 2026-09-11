package com.alpha.showcase.common.ui.play

import de.stefan_oltmann.kim.common.KimValueFormatter
import kotlin.math.abs

/** ImageIO properties describe the source image, before any display-only conversion. */
internal fun galleryImageMetadata(
    properties: Map<*, *>, fileName: String?, byteCount: Long,
    assetWidth: Int = 0, assetHeight: Int = 0, assetDate: String? = null,
    assetCoordinates: PhotoCoordinates? = null,
): MediaMetadata {
    fun Map<*, *>.text(key: String) = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    fun Map<*, *>.number(key: String): Double? = (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
    fun Map<*, *>.positive(key: String) = number(key)?.takeIf { it > 0 }
    val exif = properties["{Exif}"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val tiff = properties["{TIFF}"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val gps = properties["{GPS}"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val aux = properties["{ExifAux}"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val width = properties.positive("PixelWidth")?.toInt() ?: assetWidth
    val height = properties.positive("PixelHeight")?.toInt() ?: assetHeight
    val rotated = properties.number("Orientation")?.toInt() in 5..8
    fun coordinate(value: Double?, ref: String?, positive: String, negative: String): Double? = when (ref) {
        positive -> value?.let(::abs)
        negative -> value?.let { -abs(it) }
        else -> null
    }
    val latitude = coordinate(gps.number("Latitude"), gps.text("LatitudeRef"), "N", "S")
    val longitude = coordinate(gps.number("Longitude"), gps.text("LongitudeRef"), "E", "W")
    val exifCoordinates = if (latitude != null && longitude != null) PhotoCoordinates(latitude, longitude) else null
    val coordinates = assetCoordinates?.takeIf { it.valid } ?: exifCoordinates?.takeIf { it.valid }
    val rows = buildList {
        fun row(kind: MediaMetadataKind, value: String?) {
            value?.takeIf { it.isNotBlank() }?.let { add(MediaMetadataEntry(kind, it.take(1024))) }
        }
        val capture = exif.text("DateTimeOriginal") ?: exif.text("DateTimeDigitized") ?: tiff.text("DateTime")
        val date = capture?.takeIf { it.matches(Regex("\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}")) && !it.startsWith("0000") }
            ?.let { it.take(10).replace(':', '-') + it.drop(10) }
        val offset = exif.text("OffsetTimeOriginal")?.takeIf { it.matches(Regex("[+-]\\d{2}:\\d{2}")) }
        row(MediaMetadataKind.Date, date?.let { if (offset != null) "$it $offset" else it } ?: assetDate)
        val make = tiff.text("Make")
        val model = tiff.text("Model")
        row(MediaMetadataKind.Camera, if (make != null && model?.startsWith(make, true) == true) model else listOfNotNull(make, model).joinToString(" "))
        row(MediaMetadataKind.Lens, exif.text("LensModel") ?: aux.text("LensModel"))
        val iso = (exif["ISOSpeedRatings"] as? List<*>)?.firstOrNull() as? Number
        row(MediaMetadataKind.Exposure, listOfNotNull(
            exif.positive("FNumber")?.let(KimValueFormatter::formatFNumber),
            exif.positive("ExposureTime")?.let(KimValueFormatter::formatExposureTime),
            iso?.toInt()?.takeIf { it > 0 }?.let(KimValueFormatter::formatIso),
            exif.positive("FocalLength")?.let(::formatExifFocalLength),
            exif.positive("FocalLenIn35mmFilm")?.toInt()?.let { "35mm: $it mm" },
        ).joinToString(" · "))
        if (width > 0 && height > 0) row(MediaMetadataKind.Dimensions,
            if (rotated) formatMediaDimensions(height, width) else formatMediaDimensions(width, height))
        coordinates?.let { row(MediaMetadataKind.Location, "GPS: ${it.latitude}, ${it.longitude}") }
        row(MediaMetadataKind.Description, tiff.text("ImageDescription"))
        row(MediaMetadataKind.Author, tiff.text("Artist"))
        row(MediaMetadataKind.Copyright, tiff.text("Copyright"))
    }
    return MediaMetadata(rows, width > 0 && height > 0, byteCount, coordinates, fileName)
}
