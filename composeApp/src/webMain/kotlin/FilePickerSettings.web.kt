import io.github.vinceglb.filekit.PlatformFile

// Browser pickers use browser file handles, with no filesystem starting path.
internal actual fun Platform.directoryPickerInitialFile(): PlatformFile? = null
