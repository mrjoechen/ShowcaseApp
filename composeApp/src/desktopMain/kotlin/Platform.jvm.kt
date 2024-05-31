import java.awt.Desktop
import java.net.URI

class JVMPlatform: Platform {
    override val name: String = "${System.getProperty("os.name").lowercase()} Java ${System.getProperty("java.version")}"
    override fun openUrl(url: String) {
        val uri = URI.create(url)
        Desktop.getDesktop().browse(uri)
    }

    override fun getConfigDirectory(): String {
        val os = getPlatformName()
        return when {
            os.contains("win") -> System.getenv("APPDATA") + "\\Showcase\\"
            os.contains("mac") -> System.getProperty("user.home") + "/Library/Application Support/Showcase/"
            else -> System.getProperty("user.home") + "/.config/Showcase/"
        }
    }

}

actual fun getPlatform(): Platform = JVMPlatform()