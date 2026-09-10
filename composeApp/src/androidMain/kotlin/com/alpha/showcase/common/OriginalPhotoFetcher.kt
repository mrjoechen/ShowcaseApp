package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.toAndroidUri
import okio.buffer
import okio.source

/** Read the authorized original; Coil's normal content fetcher remains the fallback. */
internal class OriginalPhotoFetcher(private val data: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceFetchResult? {
        val resolver = options.context.contentResolver
        val uri = originalPhotoUri(options.context, data.toAndroidUri()) ?: return null
        val stream = try {
            resolver.openInputStream(uri)
        } catch (_: SecurityException) { null }
          catch (_: UnsupportedOperationException) { null }
          catch (_: java.io.FileNotFoundException) { null }
            ?: return null
        // A plain stream keeps EXIF and pixels on the same source, including SAF files.
        return SourceFetchResult(
            source = ImageSource(stream.source().buffer(), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (isPhotoContentUri(data.toAndroidUri())) OriginalPhotoFetcher(data, options) else null
    }
}

/** Redacted pixels/metadata must not be reused after the user grants location access. */
internal class PhotoLocationKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? =
        if (isPhotoContentUri(data.toAndroidUri())) {
            "photo-location:${hasPhotoLocationAccess(options.context)}:$data"
        } else null
}
