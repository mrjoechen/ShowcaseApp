import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.components.WasmScreenFeature
import com.alpha.showcase.common.networkfile.Data
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import com.alpha.showcase.common.networkfile.model.LocalFile
import kotlinx.browser.window
import kotlin.random.Random

class WasmPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.WebWasm
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

    override fun listFiles(path: String): List<LocalFile> {
        TODO("Not yet implemented")
    }
}

actual fun getPlatform(): Platform = WasmPlatform()
actual fun rclone(): Rclone? = null

@OptIn(ExperimentalStdlibApi::class)
actual fun randomUUID(): String = Random.Default.nextBytes(16).toHexString()
actual fun rService(): RService = WasmRService

object WasmRService: RService {
    override suspend fun startRService(inputData: Data, onProgress: (Data?) -> Unit) {
        println("WasmRService startRService")
    }

    override fun stopRService() {
        println("WasmRService stopRService")
    }

}

actual fun getScreenFeature(): ScreenFeature = WasmScreenFeature()