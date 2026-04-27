import com.alpha.showcase.common.components.DesktopScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.update.UpdateInstallProgress
import com.alpha.showcase.common.update.verifyFileDigestOrThrow
import com.alpha.showcase.api.github.GithubReleaseAsset
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import okio.FileSystem
import okio.Path.Companion.toPath
import io.github.mrjoechen.Once
import io.github.mrjoechen.initialise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object JVMPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Desktop
    override val name: String = "${System.getProperty("os.name")} ${System.getProperty("os.arch")} Java ${System.getProperty("java.version")}"
    override fun openUrl(url: String) {
        val uri = URI.create(url)
        Desktop.getDesktop().browse(uri)
    }

    override fun getConfigDirectory(): String {
        return when {
            isWindows() -> System.getenv("APPDATA") + "\\Showcase\\"
            isMacOS() -> System.getProperty("user.home") + "/Library/Application Support/Showcase/"
            else -> System.getProperty("user.home") + "/.config/Showcase/"
        }
    }

    override fun init() {
        Once.initialise()
    }

    override fun destroy() {

    }

    override suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        fileName: String,
        expectedDigest: String?,
        expectedSizeBytes: Long?,
        onProgress: ((UpdateInstallProgress) -> Unit)?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val updateDir = File(getConfigDirectory(), "updates").apply { mkdirs() }
                val targetName = fileName.takeIf { it.isNotBlank() }
                    ?: "showcase-update-${System.currentTimeMillis()}"
                val targetFile = File(updateDir, targetName)

                val connection = URL(downloadUrl).openConnection()
                val totalBytes = expectedSizeBytes?.takeIf { it > 0 }
                    ?: connection.contentLengthLong.takeIf { it > 0 }
                var downloadedBytes = 0L
                onProgress?.invoke(UpdateInstallProgress(downloadedBytes, totalBytes))

                connection.getInputStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress?.invoke(UpdateInstallProgress(downloadedBytes, totalBytes))
                        }
                    }
                }
                verifyFileDigestOrThrow(targetFile, expectedDigest)

                Desktop.getDesktop().open(targetFile)
            }
        }
    }

    override fun selectUpdateAssetForCurrentArchitecture(assets: List<GithubReleaseAsset>): GithubReleaseAsset? {
        if (assets.isEmpty()) return null
        val normalizedAssets = assets.map { it to it.name.lowercase(Locale.US) }
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.US)

        val archCandidate = when {
            arch.contains("aarch64") || arch.contains("arm64") -> {
                normalizedAssets.firstOrNull { (_, name) ->
                    name.containsAnyMarker(DESKTOP_ARM64_MARKERS)
                }?.first
            }
            arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> {
                normalizedAssets.firstOrNull { (_, name) ->
                    name.containsAnyMarker(DESKTOP_X64_MARKERS)
                }?.first
            }
            arch.contains("86") -> {
                normalizedAssets.firstOrNull { (_, name) ->
                    name.contains("x86") && !name.containsAnyMarker(DESKTOP_X64_MARKERS)
                }?.first
            }
            arch.contains("arm") -> {
                normalizedAssets.firstOrNull { (_, name) ->
                    name.containsAnyMarker(DESKTOP_ARM32_MARKERS)
                }?.first
            }
            else -> null
        }
        if (archCandidate != null) return archCandidate

        val universal = normalizedAssets.firstOrNull { (_, name) ->
            name.containsAnyMarker(DESKTOP_UNIVERSAL_MARKERS)
        }?.first
        if (universal != null) return universal

        val noArchMarker = normalizedAssets.firstOrNull { (_, name) ->
            !name.containsAnyMarker(DESKTOP_ARCH_MARKERS)
        }?.first
        if (noArchMarker != null) return noArchMarker

        return assets.firstOrNull()
    }

    override fun getDevice(): Device {
        val device = Device(
            id = Analytics.getInstance().deviceId,
            name = InetAddress.getLocalHost().hostName,
            model = "",
            oemName = "",
            osName = System.getProperty("os.name"),
            osVersion = System.getProperty("os.version"),
            locale = System.getProperty("user.language"),
            appVersion = versionName,
            appNameSpace = "",
            appBuild = versionHash,
            buildType = if (isDebug) "debug" else "release",
            osApi = "Java ${System.getProperty("java.version")}",
            buildId = "",
            timezoneOffset = (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toString(),
            cpuArch = System.getProperty("os.arch")
        )
        return device
    }

    override fun listFiles(path: String): List<LocalFile> {
        return FileSystem.SYSTEM.list(path.toPath()).map {
            val file = it.toFile()
            LocalFile(
                file.toString(),
                file.name,
                file.isDirectory,
                file.length(),
                file.extension,
                file.lastModified().toString()
            )
        }
    }

    private fun String.containsAnyMarker(markers: Set<String>): Boolean {
        return markers.any { marker -> contains(marker) }
    }

    private val DESKTOP_ARM64_MARKERS = setOf("arm64", "aarch64")
    private val DESKTOP_X64_MARKERS = setOf("x86_64", "x86-64", "x8664", "amd64", "x64")
    private val DESKTOP_ARM32_MARKERS = setOf("armv7", "armv7l")
    private val DESKTOP_UNIVERSAL_MARKERS = setOf("universal", "noarch", "all")
    private val DESKTOP_ARCH_MARKERS = buildSet {
        addAll(DESKTOP_ARM64_MARKERS)
        addAll(DESKTOP_X64_MARKERS)
        addAll(DESKTOP_ARM32_MARKERS)
        add("x86")
    }
}

actual val isDebug: Boolean by lazy {
    java.lang.management.ManagementFactory.getRuntimeMXBean()
        .inputArguments.any { it.contains("jdwp") }
}

actual fun getPlatform(): Platform = JVMPlatform
actual fun randomUUID() = UUID.randomUUID().toString()
actual fun ensureGalleryReadPermissionIfNeeded(): Boolean = true
actual fun persistGalleryUriPermission(uri: String) {}
actual fun getScreenFeature(): ScreenFeature = DesktopScreenFeature
