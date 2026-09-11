package com.alpha.showcase.common.ui.source

internal enum class GalleryReadAccess { NotDetermined, Limited, Full, Denied }

/** Initial limited authorization is itself the user's selection for this add operation. */
internal suspend fun selectGalleryImages(
    initialAccess: GalleryReadAccess,
    requestAccess: suspend () -> GalleryReadAccess,
    authorizedImages: suspend () -> List<String>,
    pickImages: suspend () -> List<String>,
    pickLimitedImages: suspend () -> List<String>,
): List<String> {
    val access = if (initialAccess == GalleryReadAccess.NotDetermined) requestAccess() else initialAccess
    if (access != GalleryReadAccess.Limited && access != GalleryReadAccess.Full) {
        throw GalleryPermissionDeniedException()
    }
    return if (initialAccess == GalleryReadAccess.NotDetermined && access == GalleryReadAccess.Limited) {
        authorizedImages()
    } else if (access == GalleryReadAccess.Limited) {
        pickLimitedImages()
    } else {
        pickImages()
    }.distinct()
}

/** A partially readable selection must never be reported as a successful complete import. */
internal fun requireAllGalleryImagesAccessible(selected: List<String>, accessible: Set<String>) {
    if (selected.any { it !in accessible }) throw GallerySelectionAccessException()
}

internal class GallerySelectionAccessException : Exception()

internal class GalleryAccessChangedException : Exception()

/** Each retry reads live authorization again, rather than retaining a previous launch's status. */
internal suspend fun <T> retryGallerySelectionOnAccessChange(select: suspend () -> T): T {
    while (true) {
        try {
            return select()
        } catch (_: GalleryAccessChangedException) {
            // The native selector has finished dismissing before requesting a new route.
        }
    }
}

internal fun retainAuthorizedGallerySelection(selected: Set<String>, images: List<String>): Set<String> =
    selected.intersect(images.toSet())
