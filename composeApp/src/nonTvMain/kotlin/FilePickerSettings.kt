import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType

expect fun createFilePickerDialogSettings(title: String): FileKitDialogSettings

// FileKit only provides its path constructor on filesystem platforms.
internal expect fun Platform.directoryPickerInitialFile(): PlatformFile?

fun Platform.galleryPickerType(): FileKitType =
    galleryPickerExtensions()?.let(FileKitType::File) ?: FileKitType.Image
