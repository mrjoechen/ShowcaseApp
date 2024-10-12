import androidx.compose.ui.window.ComposeUIViewController
import com.alpha.showcase.common.Startup

fun MainViewController() = run {
    onStartup()
    ComposeUIViewController {
        MainApp()
    }
}

fun onStartup() {
    Startup.run()
}