import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.util.SystemInfo
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

            Showcase().main()
        }
    }

    fun main() = application {
        FlatLaf.setup(FlatMacDarkLaf())
        FlatLaf.setUseNativeWindowDecorations(true)
        if( SystemInfo.isLinux ) {
            // enable custom window decorations
            JFrame.setDefaultLookAndFeelDecorated( true )
            JDialog.setDefaultLookAndFeelDecorated( true )
        }
        if(!SystemInfo.isJava_9_orLater && System.getProperty( "flatlaf.uiScale" ) == null )
            System.setProperty( "flatlaf.uiScale", "2x" )

        val rProcess: Process? = null
        val icon = painterResource("showcase_logo.png")
        val state = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 960.dp,
            height = 640.dp
        )
        Window(
            onCloseRequest = {
                rProcess?.destroy()
                rService().stopRService()
                exitApplication()
            },
            state = state,
            onKeyEvent = {
                println("KeyEvent: $it")
                false
            },
            icon = icon,
            title = "",
        ) {
            val jFrame: JFrame = this.window
            jFrame.minimumSize = java.awt.Dimension(480, 640)

            if (isMacOS()){
                jFrame.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                jFrame.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                MainApp()
            }

            val appSupportPath = Paths.get(getConfigDirectory())
            if (Files.notExists(appSupportPath)) {
                Files.createDirectories(appSupportPath)
            }
            println("configDir: $appSupportPath")

            val resourcesDir = File(System.getProperty("compose.application.resources.dir"))
            println("resourcesDir: $resourcesDir")

        }
    }
}

