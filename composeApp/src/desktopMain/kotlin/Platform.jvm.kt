import com.alpha.showcase.common.components.DesktopScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import com.alpha.showcase.common.networkfile.model.LocalFile
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.awt.Desktop
import java.net.URI
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

    override fun getCacheDirectory(): String = getConfigDirectory()

    override fun init() {
    }

    override fun destroy() {

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
actual fun rclone(): Rclone? = DesktopRclone()
actual fun rService(): RService? = DesktopRService

actual fun getScreenFeature(): ScreenFeature = DesktopScreenFeature