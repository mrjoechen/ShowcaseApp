import kotlinx.browser.window

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override fun openUrl(url: String) {
        window.open(url)
    }
}

actual fun getPlatform(): Platform = WasmPlatform()