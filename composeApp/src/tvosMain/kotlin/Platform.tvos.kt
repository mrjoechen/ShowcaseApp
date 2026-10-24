import com.alpha.showcase.common.networkfile.model.LocalFile

object TVOSPlatform : ApplePlatform() {

    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Tvos

    override val usesRemoteNavigation: Boolean = true
    override val supportsSharing: Boolean = false
    override val supportsImageExport: Boolean = false
    override val supportsFileArchives: Boolean = false
    // Databases, AI creations and caches are subject to tvOS storage reclamation.
    override val persistsLocalData: Boolean = false
    // Updates are delivered through the App Store / TestFlight.
    override val supportsUpdateCheck: Boolean = false
    // Links are shown as a QR sheet, which should appear without delay.
    override val opensUrlImmediately: Boolean = true
    // The face detector has no tvOS build, so Face privacy blocks every AI upload.
    override val supportsLocalFaceDetection: Boolean = false

    override fun init() {}

    // tvOS has no user-visible filesystem.
    override fun listFiles(path: String): List<LocalFile> = emptyList()
}

actual fun getPlatform(): Platform = TVOSPlatform
// tvOS has no photo library picker.
actual fun ensureGalleryReadPermissionIfNeeded(): Boolean = false
actual fun persistGalleryUriPermission(uri: String) {}

actual fun shouldRunAutomaticUpdateCheck(): Boolean = getPlatform().supportsUpdateCheck
