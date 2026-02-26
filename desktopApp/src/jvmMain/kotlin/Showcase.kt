import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
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
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
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
            configureCoroutineScheduler()
            installGlobalCrashHandlers()
            initializeSentry()
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

        LaunchedEffect(Unit){
            val appSupportPath = Paths.get(getConfigDirectory())
            if (Files.notExists(appSupportPath)) {
                Files.createDirectories(appSupportPath)
            }
            Log.d("configDir: $appSupportPath")

            val resourcesDirPath = System.getProperty("compose.application.resources.dir")
            if (resourcesDirPath.isNullOrBlank()) {
                Log.w("resourcesDir is not set for current run mode")
            } else {
                val resourcesDir = File(resourcesDirPath)
                Log.d("resourcesDir: $resourcesDir")
            }
        }

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

private fun configureCoroutineScheduler() {
    // Prevent unbounded worker growth in dense image layouts (FrameWall/Bento).
    System.setProperty("kotlinx.coroutines.scheduler.core.pool.size", "4")
    System.setProperty("kotlinx.coroutines.scheduler.max.pool.size", "16")
    System.setProperty("kotlinx.coroutines.io.parallelism", "16")
}

private fun installGlobalCrashHandlers() {
    val logFile = DesktopCrashLogger.init()
    System.err.println("[Showcase] Crash log file: ${logFile.absolutePath}")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        DesktopCrashLogger.log("Uncaught exception on thread=${thread.name}", throwable)
    }
    System.setProperty("sun.awt.exception.handler", DesktopAwtExceptionHandler::class.java.name)
    installAwtEventQueueHandler()
}

private fun installAwtEventQueueHandler() {
    val queue = Toolkit.getDefaultToolkit().systemEventQueue
    queue.push(object : EventQueue() {
        override fun dispatchEvent(event: AWTEvent) {
            try {
                super.dispatchEvent(event)
            } catch (throwable: Throwable) {
                DesktopCrashLogger.log("AWT dispatch exception for event=${event.javaClass.simpleName}", throwable)
                throw throwable
            }
        }
    })
}

@Suppress("unused")
class DesktopAwtExceptionHandler {
    fun handle(throwable: Throwable) {
        DesktopCrashLogger.log("AWT exception handler", throwable)
    }
}

private object DesktopCrashLogger {
    private val lock = Any()
    private lateinit var logFile: File
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    fun init(): File {
        val directory = runCatching {
            File(getConfigDirectory()).resolve("logs")
        }.getOrElse {
            File(System.getProperty("user.home")).resolve(".showcase/logs")
        }
        if (!directory.exists()) {
            directory.mkdirs()
        }
        logFile = directory.resolve("desktop-crash.log")
        write("Crash logger initialized")
        return logFile
    }

    fun log(message: String, throwable: Throwable) {
        val traceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(traceWriter))
        val content = buildString {
            append(message)
            append('\n')
            append(traceWriter.toString())
        }
        write(content)
        throwable.printStackTrace()
    }

    private fun write(content: String) {
        val line = "[${formatter.format(Date())}] $content\n"
        synchronized(lock) {
            runCatching {
                FileWriter(logFile, true).use { it.write(line) }
            }.onFailure {
                System.err.println("[Showcase] Failed to write crash log: ${it.message}")
            }
        }
    }
}
