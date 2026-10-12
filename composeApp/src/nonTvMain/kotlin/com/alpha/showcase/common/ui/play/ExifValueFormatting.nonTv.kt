package com.alpha.showcase.common.ui.play
import de.stefan_oltmann.kim.common.KimValueFormatter
internal actual fun formatExifAperture(value: Double): String = KimValueFormatter.formatFNumber(value)
internal actual fun formatExifExposure(value: Double): String? = KimValueFormatter.formatExposureTime(value)
internal actual fun formatExifIso(value: Int): String = KimValueFormatter.formatIso(value)
