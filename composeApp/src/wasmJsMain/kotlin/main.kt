import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.alpha.showcase.common.Startup

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Startup.run()
    CanvasBasedWindow(canvasElementId = "Showcase App") { MainApp() }
}