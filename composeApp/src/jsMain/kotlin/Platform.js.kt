import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.components.WebScreenFeature
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.utils.Device
import kotlinx.browser.window
import kotlin.random.Random

class JsPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.WebJS
    override val name: String = "${platform.platformName} ${window.navigator.userAgent}"
    override fun openUrl(url: String) {
        window.open(url)
    }

    override fun getConfigDirectory(): String {
        return ""
    }
    override fun init() {
    }

    override fun destroy() {

    }

    override fun listFiles(path: String): List<LocalFile> {
        return emptyList()
    }

    override fun getDevice(): Device {
        return webDevice(name, platform.platformName)
    }
}

actual fun getPlatform(): Platform = JsPlatform()

@OptIn(ExperimentalStdlibApi::class)
actual fun randomUUID(): String = Random.Default.nextBytes(16).toHexString()

actual fun getScreenFeature(): ScreenFeature = WebScreenFeature()
