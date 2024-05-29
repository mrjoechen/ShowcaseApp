import java.awt.Desktop
import java.net.URI

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override fun openUrl(url: String) {
        val uri = URI.create(url)
        Desktop.getDesktop().browse(uri)
    }

}

actual fun getPlatform(): Platform = JVMPlatform()