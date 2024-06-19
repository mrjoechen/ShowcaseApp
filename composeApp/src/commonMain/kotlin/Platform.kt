import com.alpha.showcase.common.networkfile.Rclone

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

fun isIos(): Boolean {
    val platform = getPlatformName().trim().lowercase()
    return platform.contains("ios") || platform.contains("iphone")
}

fun isMacOS(): Boolean {
    val platform = getPlatformName().trim().lowercase()
    return platform.contains("mac") || platform.contains("os x")
}

fun isDesktop(): Boolean {
    return getPlatformName().trim().lowercase().contains(PLATFORM_DESKTOP) or isWindows() or isMacOS()
}


const val TEST_KEY = "1234567890123456"
const val TEST_IV = "0123456789abcdef"// 长度必须是 16 个字节

expect fun randomUUID(): String

fun supportRClone() = isDesktop() || isAndroid()

expect fun rclone(): Rclone