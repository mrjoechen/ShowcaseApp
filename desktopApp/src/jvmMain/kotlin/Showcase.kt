import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.alpha.showcase.common.Startup
import com.alpha.showcase.common.components.DesktopScreenFeature
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JDialog
import javax.swing.JFrame


class Showcase{
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
//            if (isMac()){
//                System.setProperty("apple.awt.application.appearance", "system")
//                System.setProperty("apple.awt.application.name", "Showcase App")
//                System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Showcase App")
//            }
//            initializeSentry()
            Startup.run()
            Showcase().main()
        }
    }

    fun main() = application {
        FlatMacDarkLaf.setup()
        val scope = rememberCoroutineScope()
        val rProcess: Process? = null
        val icon = painterResource("showcase_logo.png")
        val state = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 960.dp,
            height = 640.dp,
            placement = WindowPlacement.Floating // Floating, Maximized, Fullscreen
        )
        Window(
            onCloseRequest = {
                rProcess?.destroy()
                exitApplication()
            },
            state = state,
            icon = icon,
            title = ""
        ) {
            val jFrame: JFrame = this.window
            jFrame.minimumSize = java.awt.Dimension(480, 640)

            if (isMacOS()){
                jFrame.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                jFrame.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            }

            if (isWindows()){
                System.setProperty("flatlaf.useWindowDecorations", "true")
            }

            if(SystemInfo.isLinux) {
                JFrame.setDefaultLookAndFeelDecorated(true)
                JDialog.setDefaultLookAndFeelDecorated(true)
            }

            Surface (modifier = Modifier.fillMaxSize()) {
                MainApp()
            }

            val appSupportPath = Paths.get(getConfigDirectory())
            if (Files.notExists(appSupportPath)) {
                Files.createDirectories(appSupportPath)
            }
            Log.d("configDir: $appSupportPath")

            val resourcesDir = File(System.getProperty("compose.application.resources.dir"))
            Log.d("resourcesDir: $resourcesDir")

        }

        var autoFullscreen by remember { mutableStateOf(false) }

        scope.launch {
            SettingsViewModel.settingsFlow.collect {
                if (it is UiState.Content){
                    autoFullscreen = it.data.autoFullScreen
                }
            }
        }

        scope.launch {
            DesktopScreenFeature.fullScreenFlow.collect {
                if (it && autoFullscreen) {
                    state.placement = WindowPlacement.Fullscreen
                } else {
                    state.placement = WindowPlacement.Floating
                }
            }
        }
    }
}

