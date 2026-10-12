@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import coil3.Image
import com.alpha.showcase.common.ai.ImageContentIdentity
import com.alpha.showcase.common.ai.decodeWithImageIdentity
import com.alpha.showcase.common.ai.completeImageNetworkFetcherFactory
import coil3.toUri
import com.alpha.showcase.common.networkfile.model.NetworkFile
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
    val coordinates: PhotoCoordinates? = null,
    val fileName: String? = null,
    val imageIdentity: ImageContentIdentity? = null,
) {
    val lines: List<String> get() = rows.map { it.text }
}

/** Fixed precision avoids exposing EXIF float-to-double conversion noise. */
internal fun formatExifFocalLength(value: Double): String {
    val hundredths = (value * 100).roundToLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}mm"
}

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

private fun mediaDisplayName(data: Any): String? = when (data) {
    is DataWithType -> data.extra?.get("displayName")?.takeIf { it.isNotBlank() } ?: mediaDisplayName(data.data)
    else -> null
}

/** Use display fields, never an object's toString() or an authenticated URL's query. */
internal fun mediaMetadataLines(state: MediaItemState): List<String> = mediaMetadataRows(state).map { it.text }

internal fun mediaMetadataRows(state: MediaItemState): List<MediaMetadataEntry> = buildList {
    fun row(kind: MediaMetadataKind, text: String) { add(MediaMetadataEntry(kind, text.take(1024))) }
    val data = mediaSource(state.data)
    val metadata = state.metadata
    val path = when (data) {
        is NetworkFile -> data.fileName
        is UrlWithAuth -> data.url.toUri().path
        is String -> if (data.startsWith("phasset://")) null else if (data.contains("://")) data.toUri().path else data.substringBefore('?').substringBefore('#')
        else -> null
    }
    val fileName = mediaDisplayName(state.data) ?: metadata?.fileName ?: path?.replace('\\', '/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    fileName?.let { row(MediaMetadataKind.FileName, it) }
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
internal val Options.readsMediaMetadata: Boolean get() = extras[metadataEnabled] == true
internal class MediaSourceMetadata(val value: MediaMetadata?, val identity: ImageContentIdentity? = null) : coil3.decode.ImageSource.Metadata()

private val metadataResult = Extras.Key<MediaMetadata?>(null)
private const val METADATA_EXTRA = "showcase#media_metadata"
private const val METADATA_READY_EXTRA = "showcase#media_metadata_ready"

/** Separate playback entries from thumbnail entries that were decoded without metadata. */
internal fun ImageRequest.Builder.withMediaMetadata() = apply {
    extras[metadataEnabled] = true
    memoryCacheKeyExtra(METADATA_EXTRA, "1")
    memoryCacheKeyExtra("showcase#file_identity", "sha256-file-v1")
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
        add(completeImageNetworkFetcherFactory())
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
                val identified = decodeWithImageIdentity(result, options) { verified -> decodeSnapshot(verified) }
                if (identified.result != null && identified.identity != null) {
                    resultMetadata.value = (resultMetadata.value ?: MediaMetadata(emptyList())).copy(imageIdentity = identified.identity)
                }
                return identified.result
            }
            private suspend fun decodeSnapshot(result: SourceFetchResult): coil3.decode.DecodeResult? {
                val fileSize = sourceLength ?: try {
                    result.source.fileOrNull()?.let { path -> result.source.fileSystem.metadataOrNull(path)?.size }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
                val metadata = readSourceMetadata(result.source.source().peek(), fileSize)
                val sourceMetadata = (result.source.metadata as? MediaSourceMetadata)?.value
                resultMetadata.value = if (sourceMetadata != null) sourceMetadata.copy(
                    rows = sourceMetadata.rows + metadata?.rows.orEmpty().filter { row -> sourceMetadata.rows.none { it.kind == row.kind } },
                    coordinates = sourceMetadata.coordinates ?: metadata?.coordinates,
                    hasDimensions = sourceMetadata.hasDimensions || metadata?.hasDimensions == true,
                ) else metadata?.copy(fileSize = fileSize)
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


internal expect fun readSourceMetadata(source: BufferedSource, sourceLength: Long?): MediaMetadata?
