import androidx.compose.ui.text.toLowerCase

interface Platform {
    val name: String
    fun openUrl(url: String)
    fun getConfigDirectory(): String
}


const val PLATFORM_ANDROID = "Android"
const val PLATFORM_IOS = "iOS"
const val PLATFORM_DESKTOP = "Desktop"
const val PLATFORM_WINDOWS = "Windows"
const val PLATFORM_MACOS = "macOS"

expect fun getPlatform(): Platform

fun getConfigDirectory(): String {
    return getPlatform().getConfigDirectory()
}

fun getPlatformName(): String {
    return getPlatform().name
}

fun isAndroid(): Boolean {
    return getPlatformName().trim().lowercase().contains("android")
}

fun isWindows(): Boolean {
    return getPlatformName().trim().lowercase().contains("windows")
}

fun isMacOS(): Boolean {
    return getPlatformName().trim().lowercase().contains("mac")
}

fun isDesktop(): Boolean {
    return getPlatformName().trim().lowercase().contains(PLATFORM_DESKTOP) or isWindows() or isMacOS()
}