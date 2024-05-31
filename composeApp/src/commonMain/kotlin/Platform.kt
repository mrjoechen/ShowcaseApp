interface Platform {
    val name: String
    fun openUrl(url: String)
    fun getConfigDirectory(): String
}

expect fun getPlatform(): Platform

fun getConfigDirectory(): String {
    return getPlatform().getConfigDirectory()
}

fun getPlatformName(): String {
    return getPlatform().name
}

fun isAndroid(): Boolean {
    return getPlatformName().contains("Android")
}

fun isWindows(): Boolean {
    return getPlatformName().contains("win")
}

fun isMac(): Boolean {
    return getPlatformName().contains("mac")
}