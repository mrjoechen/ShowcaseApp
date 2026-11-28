package com.alpha.showcase.common.ui.play

import AndroidApp
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.alpha.showcase.common.NetworkFileReader
import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.FileOutputStream

private const val MAX_VIDEO_CACHE_BYTES = 500L * 1024L * 1024L

private sealed interface ResolvedVideoSource {
    data class LocalFile(val path: String, val rotationDegrees: Int) : ResolvedVideoSource
    data class RemoteUrl(val url: String, val headers: Map<String, String>) : ResolvedVideoSource
}

@Composable
internal actual fun PlatformVideoPlayer(
    data: Any,
    modifier: Modifier,
    contentScale: ContentScale,
    active: Boolean,
    muted: Boolean,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCompleted: () -> Unit,
) {
    val latestActive by rememberUpdatedState(active)
    val latestMuted by rememberUpdatedState(muted)
    val latestContentScale by rememberUpdatedState(contentScale)
    val latestOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnCompleted by rememberUpdatedState(onCompleted)

    var resolvedSource by remember(data) { mutableStateOf<ResolvedVideoSource?>(null) }

    LaunchedEffect(data) {
        resolvedSource = null
        latestOnLoadingChanged(true)
        try {
            resolvedSource = resolveVideoSource(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ShowcaseVideo", "Failed to resolve video source: ${e.message}", e)
            latestOnLoadingChanged(false)
            latestOnError(e.message ?: "Failed to load video")
        }
    }

    val controller = remember(data) { AndroidVideoController() }

    LaunchedEffect(active, resolvedSource) {
        controller.setActive(active)
    }

    LaunchedEffect(contentScale) {
        controller.updateContentScale(contentScale)
    }

    LaunchedEffect(muted) {
        controller.setMuted(muted)
    }

    DisposableEffect(data) {
        onDispose {
            controller.release()
        }
    }

    val source = resolvedSource
    if (source != null) {
        androidx.compose.runtime.key(source) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            controller.attach(
                                textureView = this@apply,
                                surfaceTexture = surfaceTexture,
                                source = source,
                                contentScale = latestContentScale,
                                active = latestActive,
                                muted = latestMuted,
                                onPrepared = { latestOnLoadingChanged(false) },
                                onError = { msg ->
                                    latestOnLoadingChanged(false)
                                    latestOnError(msg)
                                },
                                onCompleted = { latestOnCompleted() },
                            )
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            controller.applyTransform()
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            controller.release()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                    }
                }
            },
            update = { textureView ->
                controller.updateContentScale(contentScale)
                controller.setMuted(muted)
                if (textureView.isAvailable) {
                    controller.setActive(active)
                }
            },
        )
        }
    }
}

private class AndroidVideoController {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var textureView: TextureView? = null
    private var contentScale: ContentScale = ContentScale.Fit
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var rotationDegrees: Int = 0
    private var prepared: Boolean = false
    private var active: Boolean = false
    private var muted: Boolean = false
    private var completed: Boolean = false
    private var released: Boolean = false

    fun attach(
        textureView: TextureView,
        surfaceTexture: SurfaceTexture,
        source: ResolvedVideoSource,
        contentScale: ContentScale,
        active: Boolean,
    muted: Boolean,
        onPrepared: () -> Unit,
        onError: (String) -> Unit,
        onCompleted: () -> Unit,
    ) {
        release()
        released = false
        this.textureView = textureView
        this.contentScale = contentScale
        this.active = active
        this.muted = muted
        this.completed = false
        this.prepared = false
        this.rotationDegrees = (source as? ResolvedVideoSource.LocalFile)?.rotationDegrees ?: 0

        val newSurface = Surface(surfaceTexture)
        this.surface = newSurface

        val mp = MediaPlayer()
        this.mediaPlayer = mp
        try {
            mp.setSurface(newSurface)
            when (source) {
                is ResolvedVideoSource.LocalFile -> mp.setDataSource(source.path)
                is ResolvedVideoSource.RemoteUrl -> {
                    mp.setDataSource(AndroidApp, Uri.parse(source.url), source.headers)
                }
            }
            mp.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                applyTransform()
            }
            mp.setOnPreparedListener { player ->
                if (released) return@setOnPreparedListener
                prepared = true
                videoWidth = player.videoWidth
                videoHeight = player.videoHeight
                applyTransform()
                applyVolume()
                onPrepared()
                if (this.active) {
                    runCatching { player.start() }.onFailure { Log.e("ShowcaseVideo", "start() failed", it) }
                }
            }
            mp.setOnCompletionListener {
                if (released) return@setOnCompletionListener
                completed = true
                if (this.active) {
                    onCompleted()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e("ShowcaseVideo", "MediaPlayer error: what=$what extra=$extra")
                if (!released) {
                    onError("Video playback error ($what/$extra)")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            release()
            onError(e.message ?: "Failed to prepare video")
        }
    }

    fun setActive(isActive: Boolean) {
        active = isActive
        val mp = mediaPlayer ?: return
        if (!prepared || released) return
        runCatching {
            if (isActive) {
                if (completed) {
                    completed = false
                    mp.seekTo(0)
                }
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        }
    }

    fun setMuted(isMuted: Boolean) {
        muted = isMuted
        applyVolume()
    }

    private fun applyVolume() {
        val mp = mediaPlayer ?: return
        if (released) return
        val volume = if (muted) 0f else 1f
        runCatching { mp.setVolume(volume, volume) }
    }

    fun updateContentScale(scale: ContentScale) {
        if (contentScale != scale) {
            contentScale = scale
            applyTransform()
        }
    }

    fun applyTransform() {
        val view = textureView ?: return
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val swap = rotationDegrees == 90 || rotationDegrees == 270
        val effectiveW = if (swap) videoHeight else videoWidth
        val effectiveH = if (swap) videoWidth else videoHeight

        val viewAspect = vw.toFloat() / vh.toFloat()
        val videoAspect = effectiveW.toFloat() / effectiveH.toFloat()

        val scaleX: Float
        val scaleY: Float
        if (contentScale == ContentScale.Crop) {
            if (videoAspect > viewAspect) {
                scaleX = videoAspect / viewAspect
                scaleY = 1f
            } else {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            }
        } else {
            if (videoAspect > viewAspect) {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            } else {
                scaleX = videoAspect / viewAspect
                scaleY = 1f
            }
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
        view.setTransform(matrix)
    }

    fun release() {
        released = true
        prepared = false
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            runCatching { mp.setSurface(null) }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        val s = surface
        surface = null
        if (s != null) {
            runCatching { s.release() }
        }
        textureView = null
    }
}

private suspend fun resolveVideoSource(raw: Any): ResolvedVideoSource = withContext(Dispatchers.IO) {
    val data = if (raw is DataWithType) raw.data else raw
    when (data) {
        is NetworkFile -> {
            val scheme = data.path.substringBefore("://").lowercase()
            if (scheme in setOf("smb", "ftp", "sftp")) {
                val cached = cacheNetworkVideo(data)
                ResolvedVideoSource.LocalFile(cached.absolutePath, readVideoRotation(cached.absolutePath))
            } else if (scheme == "http" || scheme == "https") {
                ResolvedVideoSource.RemoteUrl(data.path, emptyMap())
            } else {
                ResolvedVideoSource.LocalFile(data.path, readVideoRotation(data.path))
            }
        }
        is UrlWithAuth -> {
            ResolvedVideoSource.RemoteUrl(data.url, mapOf(data.key to data.value))
        }
        is String -> {
            val lower = data.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                ResolvedVideoSource.RemoteUrl(data, emptyMap())
            } else {
                ResolvedVideoSource.LocalFile(data, readVideoRotation(data))
            }
        }
        else -> throw IllegalArgumentException("Unsupported video source: ${data::class.simpleName}")
    }
}

private suspend fun cacheNetworkVideo(file: NetworkFile): File {
    val cacheDir = File(AndroidApp.cacheDir, "video_cache").apply { mkdirs() }
    val ext = file.fileName.substringAfterLast('.', "mp4").lowercase().take(8)
    val key = "${file.remote.name}|${file.path}|${file.size}|${file.modTime}".encodeUtf8().sha256().hex()
    val target = File(cacheDir, "$key.$ext")
    if (target.exists() && target.length() > 0L && (file.size <= 0L || target.length() == file.size)) {
        target.setLastModified(System.currentTimeMillis())
        return target
    }

    val tmp = File(cacheDir, "$key.$ext.tmp")
    try {
        NetworkFileReader.getInstance().readFile(file).getOrThrow().use { input ->
            FileOutputStream(tmp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        currentCoroutineContext().ensureActive()
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        pruneVideoCache(cacheDir, keep = target)
        return target
    } catch (e: Throwable) {
        runCatching { tmp.delete() }
        throw e
    }
}

private fun pruneVideoCache(cacheDir: File, keep: File) {
    val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_VIDEO_CACHE_BYTES) return
    for (file in files.sortedBy { it.lastModified() }) {
        if (total <= MAX_VIDEO_CACHE_BYTES) break
        if (file.absolutePath == keep.absolutePath) continue
        val len = file.length()
        if (file.delete()) {
            total -= len
        }
    }
}

private fun readVideoRotation(path: String): Int {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    } finally {
        runCatching { retriever.release() }
    }
}
