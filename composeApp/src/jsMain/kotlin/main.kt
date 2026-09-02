import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import com.alpha.showcase.common.Startup
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import org.jetbrains.skiko.wasm.onWasmReady
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.MiSansNormal

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    window.asDynamic().ShowcaseStartup?.setStage("engine")
    onWasmReady {
        val startupFailure = Startup.run().exceptionOrNull()
        val startupError = startupFailure?.let { it.message ?: it.toString() }
        ComposeViewport(document.getElementById("showcase-app") ?: document.body!!) {
            val webFont = if (startupError == null) preloadFont(Res.font.MiSansNormal).value else null
            if (startupError != null || webFont != null) {
                SideEffect {
                    window.asDynamic().ShowcaseStartup?.setStage("render")
                    window.asDynamic().ShowcaseStartup?.ready()
                }
                Box(Modifier.fillMaxSize()) {
                    MainApp(
                        fontFamily = webFont?.let { FontFamily(it) } ?: FontFamily.Default,
                        startupError = startupError,
                    )
                }
            } else {
                SideEffect { window.asDynamic().ShowcaseStartup?.setStage("font") }
            }
        }
    }
}
