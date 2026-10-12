import com.alpha.showcase.api.github.GithubReleaseAsset
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.sync.CloudKeyValueStore
import com.alpha.showcase.common.update.UpdateInstallProgress
import com.alpha.showcase.common.utils.Device
import okio.Path.Companion.toPath

data class DirectoryPickerLocation(val path: String)

interface Platform {
    val platform: PLATFORM_TYPE
    val name: String
    val isTestFlight: Boolean get() = false

    /** Focus-driven input (TV remote) instead of touch/pointer; enables focus styling and D-pad helpers. */
    val usesRemoteNavigation: Boolean get() = false
    /** The system share sheet / clipboard image share is available. */
    val supportsSharing: Boolean get() = true
    /** Images can be saved to the user's photo library or a user-chosen file. */
    val supportsImageExport: Boolean get() = true
    /** Archives can be exported to and imported from user-chosen files. */
    val supportsFileArchives: Boolean get() = true
    /** The app can check for and present new releases itself. */
    val supportsUpdateCheck: Boolean get() = true
    /** Files in the app's data directory are kept until the app is deleted (tvOS may reclaim them). */
    val persistsLocalData: Boolean get() = true
    /** Open user-initiated links without a delay (web must keep user activation; TV shows a QR sheet). */
    val opensUrlImmediately: Boolean get() = false
    /** Donation entries may be shown; public App Store builds must not link to external donations. */
    val supportsDonation: Boolean get() = true
    /** Unsplash and Pexels can be offered as sources; they are held back from public App Store builds. */
    val supportsStockPhotoSources: Boolean get() = true
    /** Account-scoped key-value store used to sync configuration across the user's devices. */
    val configSyncStore: CloudKeyValueStore? get() = null
    fun openUrl(url: String)
    fun getConfigDirectory(): String
    /** Optional starting location for the local-source folder picker. */
    fun directoryPickerInitialDirectory(): DirectoryPickerLocation? = null
    fun galleryPickerExtensions(): List<String>? = null
    suspend fun preparePhotoLocationAccess() {}
    fun getCacheDirectory(): String = getPlatform().getConfigDirectory().toPath().resolve("cache").toString()
    fun init()
    fun destroy()
    fun listFiles(path: String): List<LocalFile>
    /** Filesystem platforms can resolve aliases to prevent recursive directory cycles. */
    fun directoryTraversalKey(path: String): String = path
    fun getDevice(): Device
    suspend fun clearCache() {
        clearPlatformImageCache()
    }

    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        fileName: String,
        expectedDigest: String? = null,
        expectedSizeBytes: Long? = null,
        onProgress: ((UpdateInstallProgress) -> Unit)? = null
    ): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Update install is not supported on $name"))
    }

    fun selectUpdateAssetForCurrentArchitecture(assets: List<GithubReleaseAsset>): GithubReleaseAsset? {
        return assets.firstOrNull()
    }
}


const val PLATFORM_ANDROID = "Android"
const val PLATFORM_IOS = "iOS"
const val PLATFORM_DESKTOP = "Desktop"
const val PLATFORM_WINDOWS = "Windows"
const val PLATFORM_MACOS = "macOS"
const val PLATFORM_LINUX = "Linux"
const val PLATFORM_WEB = "Web"
const val PLATFORM_WEB_WASM = "Web/Wasm"
const val PLATFORM_WEB_JS = "Web/Js"

enum class PLATFORM_TYPE(val intValue: Int, val platformName: String) {
    UNKNOWN(0b00000000, "Unknown"),
    Android(0b00000001, PLATFORM_ANDROID),
    Ios(0b00000010, PLATFORM_IOS),
    Web(0b00000100, PLATFORM_WEB),
    WebWasm(0b00000101, PLATFORM_WEB_WASM),
    WebJS(0b00000110, PLATFORM_WEB_JS),
    Desktop(0b00001000, PLATFORM_DESKTOP),
    Windows(0b00001001, PLATFORM_WINDOWS),
    MacOS(0b00001010, PLATFORM_MACOS),
    Linux(0b00001011, PLATFORM_LINUX)
}

expect fun getScreenFeature(): ScreenFeature

expect fun getPlatform(): Platform

expect fun ensureGalleryReadPermissionIfNeeded(): Boolean

expect fun persistGalleryUriPermission(uri: String)


fun getConfigDirectory(): String {
    return getPlatform().getConfigDirectory()
}

fun getPlatformName(): String {
    return getPlatform().name
}

fun isAndroid(): Boolean = getPlatform().platform == PLATFORM_TYPE.Android

fun isWindows(): Boolean {
    return getPlatformName().trim().lowercase().contains("windows")
}

fun isIos(): Boolean = getPlatform().platform == PLATFORM_TYPE.Ios

fun usesRemoteNavigation(): Boolean = getPlatform().usesRemoteNavigation

fun isIpad(): Boolean {
    return getPlatformName().trim().lowercase().contains("ipados")
}

fun isMacOS(): Boolean {
    val platform = getPlatformName().trim().lowercase()
    return platform.contains("mac") || platform.contains("os x")
}

fun isDesktop(): Boolean {
    return (getPlatform().platform == PLATFORM_TYPE.Desktop) or isWindows() or isMacOS()
}

fun isLinux(): Boolean{
    return isDesktop() && !isWindows() && !isMacOS()
}

fun isWeb(): Boolean {
    return getPlatform().platform == PLATFORM_TYPE.Web || getPlatform().platform == PLATFORM_TYPE.WebWasm || getPlatform().platform == PLATFORM_TYPE.WebJS
}

fun isMobile(): Boolean {
    return isAndroid() || isIos()
}

expect val isDebug: Boolean

expect fun randomUUID(): String

expect fun shouldRunAutomaticUpdateCheck(): Boolean

expect suspend fun clearPlatformImageCache()
