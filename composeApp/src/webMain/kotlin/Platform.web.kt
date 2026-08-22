import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import kotlinx.browser.window

actual val isDebug: Boolean = false

actual fun ensureGalleryReadPermissionIfNeeded(): Boolean = true

actual fun persistGalleryUriPermission(uri: String) = Unit

actual fun createFilePickerDialogSettings(title: String): FileKitDialogSettings =
    FileKitDialogSettings.createDefault()

internal fun webDevice(name: String, osName: String): Device = Device(
    id = Analytics.getInstance().deviceId,
    name = name,
    model = "Browser",
    oemName = "",
    osName = osName,
    osVersion = "",
    locale = window.navigator.language,
    screenSize = "${window.screen.width}x${window.screen.height}",
    appVersion = versionName,
    appNameSpace = "com.alpha.showcase.web",
    appBuild = versionHash,
    buildType = if (isDebug) "debug" else "release",
    osApi = "",
    buildId = "",
    timezoneOffset = "",
    cpuArch = null,
)
