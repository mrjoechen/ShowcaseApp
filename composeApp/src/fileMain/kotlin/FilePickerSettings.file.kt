import io.github.vinceglb.filekit.PlatformFile

internal actual fun Platform.directoryPickerInitialFile(): PlatformFile? =
    directoryPickerInitialDirectory()?.let { PlatformFile(it.path) }
