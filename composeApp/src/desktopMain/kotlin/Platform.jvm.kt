import com.alpha.showcase.common.components.DesktopScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import java.awt.Desktop
import java.net.URI
import java.util.UUID

class JVMPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Desktop
    override val name: String = "${System.getProperty("os.name")} Java ${System.getProperty("java.version")}"
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

}

actual fun getPlatform(): Platform = JVMPlatform()
actual fun randomUUID() = UUID.randomUUID().toString()
actual fun rclone(): Rclone = DesktopRclone()
actual fun rService(): RService = DesktopRService

actual fun getScreenFeature(): ScreenFeature = DesktopScreenFeature()