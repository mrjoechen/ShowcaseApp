import java.awt.Desktop
import java.net.URI

class JVMPlatform: Platform {
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