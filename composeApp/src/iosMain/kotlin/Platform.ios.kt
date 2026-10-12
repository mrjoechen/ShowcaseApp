import com.alpha.showcase.common.networkfile.model.LocalFile
import io.github.mrjoechen.Once
import io.github.mrjoechen.initialise
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.lastModified
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import platform.Foundation.NSURL

object IOSPlatform : ApplePlatform() {

    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Ios

    override fun init() {
        Once.initialise()
    }

    override fun listFiles(path: String): List<LocalFile> {
        if (path.isBlank()) {
            return emptyList()
        }

        val resolvedPath = if (path.startsWith("file://")) {
            NSURL.URLWithString(path)?.path ?: path
        } else {
            path
        }

        return runCatching {
            PlatformFile(resolvedPath).list().map { file ->
                val isDirectory = runCatching { file.isDirectory() }.getOrDefault(false)
                LocalFile(
                    path = file.path,
                    fileName = file.name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else runCatching { file.size() }.getOrDefault(0L),
                    mimeType = file.extension,
                    modTime = runCatching { file.lastModified().toString() }.getOrDefault(""),
                )
            }
        }.getOrElse {
            emptyList()
        }
    }
}

actual fun getPlatform(): Platform = IOSPlatform
// iOS authorization is asynchronous and is awaited by the native gallery picker after dialog dismissal.
actual fun ensureGalleryReadPermissionIfNeeded(): Boolean = true
actual fun persistGalleryUriPermission(uri: String) {}

actual fun createFilePickerDialogSettings(title: String): FileKitDialogSettings =
    FileKitDialogSettings.createDefault()
