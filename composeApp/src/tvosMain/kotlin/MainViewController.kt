import androidx.compose.ui.window.ComposeUIViewController
import com.alpha.showcase.common.Startup

fun MainViewController() = run {
    Startup.run().getOrThrow()
    ComposeUIViewController {
        MainApp()
    }
}
