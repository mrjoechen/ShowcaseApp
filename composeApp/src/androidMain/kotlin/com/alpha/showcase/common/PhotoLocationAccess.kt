package com.alpha.showcase.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import currentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private var requestedPhotoLocation = false

internal fun hasPhotoLocationAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_MEDIA_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

internal suspend fun requestPhotoLocationAccess(): Unit = withContext(Dispatchers.Main.immediate) {
    val activity = currentActivity ?: return@withContext
    if (hasPhotoLocationAccess(activity) || requestedPhotoLocation) return@withContext
    requestedPhotoLocation = true
    suspendCancellableCoroutine { continuation ->
        var launcher: ActivityResultLauncher<String>? = null
        launcher = activity.activityResultRegistry.register(
            "photo-location-${java.util.UUID.randomUUID()}",
            ActivityResultContracts.RequestPermission(),
        ) {
            launcher?.unregister()
            if (continuation.isActive) continuation.resume(Unit)
        }
        continuation.invokeOnCancellation { launcher?.unregister() }
        try {
            launcher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } catch (_: IllegalStateException) {
            launcher.unregister()
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

/** Photo Picker grants do not authorize conversion to an arbitrary MediaStore item. */
internal fun isPhotoContentUri(uri: Uri): Boolean = uri.scheme == "content" && when (uri.authority) {
    "com.android.externalstorage.documents", "com.android.providers.media.documents" -> true
    MediaStore.AUTHORITY -> uri.pathSegments.let {
        "images" in it || it.firstOrNull() == "picker_get_content"
    }
    else -> false
}

internal fun originalPhotoUri(context: Context, uri: Uri): Uri? {
    if (Build.VERSION.SDK_INT < 29 || !hasPhotoLocationAccess(context) || !isPhotoContentUri(uri)) return null
    val mediaUri = if (uri.authority == MediaStore.AUTHORITY) uri else {
        try {
            MediaStore.getMediaUri(context, uri)
        } catch (_: SecurityException) { null }
          catch (_: IllegalArgumentException) { null }
    } ?: return null
    return MediaStore.setRequireOriginal(mediaUri)
}
