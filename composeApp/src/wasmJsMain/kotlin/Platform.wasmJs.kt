import com.alpha.showcase.common.networkfile.Data
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import kotlinx.browser.window

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override fun openUrl(url: String) {
        window.open(url)
    }

    override fun getConfigDirectory(): String {
        return ""
    }
}

actual fun getPlatform(): Platform = WasmPlatform()
actual fun rclone(): Rclone = WasmRclone()

actual fun randomUUID(): String = js("require('uuid').v4()")
actual fun rService(): RService = WasmRService

object WasmRService: RService {
    override suspend fun startRService(inputData: Data, onProgress: (Data?) -> Unit) {
        println("WasmRService startRService")
    }

    override fun stopRService() {
        println("WasmRService stopRService")
    }

}
