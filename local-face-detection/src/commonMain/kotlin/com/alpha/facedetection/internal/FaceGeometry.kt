package com.alpha.facedetection.internal

import kotlin.math.roundToInt

internal const val SCORE_THRESHOLD = 0.6f
internal const val NMS_THRESHOLD = 0.3f
internal const val TOP_K = 5_000
internal const val RESULT_COLUMNS = 15

internal data class ImageSize(val width: Int, val height: Int)

internal fun detectionSize(width: Int, height: Int): ImageSize {
    require(width > 0 && height > 0)
    val scale = minOf(1.0, 640.0 / maxOf(width, height))
    return ImageSize(
        maxOf(1, (width * scale).roundToInt()),
        maxOf(1, (height * scale).roundToInt()),
    )
}

/** Retains the source adapter's score filter and clipped, nonempty bounds check. */
internal fun isVisibleFace(row: FloatArray, width: Int, height: Int): Boolean {
    require(row.size >= RESULT_COLUMNS && width > 0 && height > 0)
    val score = row[14]
    if (!score.isFinite() || score < SCORE_THRESHOLD) return false
    if ((0..3).any { !row[it].isFinite() }) return false
    val left = (row[0] / width).coerceIn(0f, 1f)
    val top = (row[1] / height).coerceIn(0f, 1f)
    val right = ((row[0] + row[2]) / width).coerceIn(0f, 1f)
    val bottom = ((row[1] + row[3]) / height).coerceIn(0f, 1f)
    return right > left && bottom > top
}
