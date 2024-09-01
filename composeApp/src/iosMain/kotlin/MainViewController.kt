import androidx.compose.ui.window.ComposeUIViewController
import com.alpha.showcase.common.Startup

fun MainViewController() = ComposeUIViewController { MainApp() }

fun onStartup() {
    Startup.run()
}