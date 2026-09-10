package com.alpha.showcase.common.ui.play

// No system geocoder is available on this target. Preserve EXIF location/coordinates.
internal actual suspend fun nativePhotoAddress(coordinates: PhotoCoordinates, language: String): String? = null
