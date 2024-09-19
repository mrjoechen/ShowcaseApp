import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alpha.showcase.common.Startup
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() = onWasmReady{
    Startup.run()
    ComposeViewport(document.body!!) {
        MainApp()
    }
}