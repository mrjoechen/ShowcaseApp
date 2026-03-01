import com.alpha.showcase.common.components.DesktopScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import okio.FileSystem
import okio.Path.Companion.toPath
import java.awt.Desktop
import java.net.InetAddress
import java.net.URI
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
    }

    override fun destroy() {

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
            buildType = "debug",
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

}

actual fun getPlatform(): Platform = JVMPlatform
actual fun randomUUID() = UUID.randomUUID().toString()
actual fun ensureGalleryReadPermissionIfNeeded(): Boolean = true
actual fun persistGalleryUriPermission(uri: String) {}
actual fun getScreenFeature(): ScreenFeature = DesktopScreenFeature
