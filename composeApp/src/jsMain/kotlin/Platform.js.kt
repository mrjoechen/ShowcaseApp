import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.components.WebScreenFeature
import com.alpha.showcase.common.networkfile.Data
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import kotlinx.browser.window
import kotlin.random.Random

class WasmPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.WebJS
    override val name: String = platform.platformName
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
}

actual fun getPlatform(): Platform = WasmPlatform()
actual fun rclone(): Rclone = JsRclone()

@OptIn(ExperimentalStdlibApi::class)
actual fun randomUUID(): String = Random.Default.nextBytes(16).toHexString()
actual fun rService(): RService = JsRService

object JsRService: RService {
    override suspend fun startRService(inputData: Data, onProgress: (Data?) -> Unit) {
        println("jsRService startRService")
    }

    override fun stopRService() {
        println("jsRService stopRService")
    }

}

actual fun getScreenFeature(): ScreenFeature = WebScreenFeature()