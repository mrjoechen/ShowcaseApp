import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.compose.ui.window.ComposeViewport
import com.alpha.showcase.common.Startup
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Startup.run()
//    CanvasBasedWindow(canvasElementId = "Showcase App") { MainApp() }
    ComposeViewport(document.body!!) {
        MainApp()
    }
}