import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import com.alpha.showcase.common.Startup
import kotlinx.browser.document
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.MiSansNormal

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    val startupError = Startup.run().exceptionOrNull()?.message
    ComposeViewport(document.body!!) {
        if (startupError != null) {
            MainApp(startupError = startupError)
        } else {
            val webFont = preloadFont(Res.font.MiSansNormal).value
            if (webFont != null) {
                MainApp(fontFamily = FontFamily(webFont))
            }
        }
    }
}
