import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alpha.showcase.common.Startup
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Startup.run()
    ComposeViewport(document.body!!) {
        MainApp()
    }
}