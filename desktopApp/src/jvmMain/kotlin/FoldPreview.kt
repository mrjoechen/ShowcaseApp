import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.alpha.showcase.common.ui.play.fold.FoldImageDemo

/** Run with ./gradlew :desktopApp:run --args=--fold-preview */
internal fun showFoldPreview() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Showcase · Photo fold preview",
        state = rememberWindowState(width = 1100.dp, height = 940.dp),
    ) {
        FoldImageDemo()
    }
}
