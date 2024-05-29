import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application


class Showcase {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
//            if (AppConfig.isMac()){
//            System.setProperty("apple.awt.application.appearance", "system")
//                System.setProperty("apple.awt.application.name", StringResources.current.app_name)
//                System.setProperty("com.apple.mrj.application.apple.menu.about.name", StringResources.current.app_name)
//            }

            Showcase().main()
        }
    }

    fun main() = application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ShowcaseApp",
        ) {
            App()
        }
    }

}
