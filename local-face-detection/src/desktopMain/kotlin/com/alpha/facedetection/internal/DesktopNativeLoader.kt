package com.alpha.facedetection.internal

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties

/** One successful load per application class loader; failed attempts remain retryable. */
internal object DesktopNativeLoader {
    private val runtime = DesktopNativeRuntime()

    fun load() = runtime.load()
}

/** The resource and absolute-path load functions are the only native/runtime boundaries. */
internal class DesktopNativeRuntime(
    private val osName: String = System.getProperty("os.name", ""),
    private val osArch: String = System.getProperty("os.arch", ""),
    private val resource: (String) -> InputStream? = { DesktopNativeLoader::class.java.getResourceAsStream(it) },
    private val loadLibrary: (String) -> Unit = { System.load(it) },
    private val tempRoot: Path? = null,
) {
    // Kotlin's synchronized lazy publishes success only, and retries after initializer exceptions.
    private val loaded: Unit by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadPackagedLibrary() }

    fun load() = loaded

    private fun loadPackagedLibrary() {
        val platform = platform()
        val library = when (platform.substringBefore('-')) {
            "windows" -> "opencv_java4120.dll"
            "macos" -> "libopencv_java4120.dylib"
            else -> "libopencv_java4120.so"
        }
        val base = "/com/alpha/facedetection/native/$platform"
        var directory: Path? = null
        try {
            val metadata = Properties()
            openResource("$base/native.properties").use { metadata.load(it) }
            require(metadata.getProperty("version") == "4.12.0") { "Expected native version 4.12.0" }
            require(metadata.getProperty("platform") == platform) { "Native manifest platform must be $platform" }
            // Never resolve a manifest-supplied path: only the fixed filename for this platform is allowed.
            require(metadata.getProperty("library") == library) { "Native manifest library must be $library" }
            val expected = metadata.getProperty("sha256")
            require(expected != null && expected.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Native manifest must contain a 64-digit SHA-256"
            }

            directory = createPrivateDirectory()
            val nativeFile = directory.resolve(library)
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(openResource("$base/$library"), digest).use { input ->
                Files.newOutputStream(nativeFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                    input.copyTo(it)
                }
            }
            val expectedBytes = ByteArray(32) { expected.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            require(MessageDigest.isEqual(expectedBytes, digest.digest())) { "Native library SHA-256 mismatch" }
            loadLibrary(nativeFile.toAbsolutePath().toString())
        } catch (failure: Exception) {
            throw loadFailure(platform, failure)
        } catch (failure: UnsatisfiedLinkError) {
            throw loadFailure(platform, failure)
        } finally {
            directory?.let { cleanup(it, it.resolve(library)) }
        }
    }

    private fun openResource(path: String): InputStream = resource(path)
        ?: throw IOException("Missing packaged OpenCV runtime resource: $path")

    private fun loadFailure(platform: String, cause: Throwable): UnsatisfiedLinkError =
        UnsatisfiedLinkError("Cannot load packaged OpenCV 4.12.0 for $platform: ${cause.message}").apply {
            initCause(cause)
        }

    private fun platform(): String {
        val os = osName.trim().lowercase(Locale.ROOT)
        val family = when {
            os == "windows" || os.startsWith("windows ") -> "windows"
            os == "linux" -> "linux"
            os == "mac os x" || os == "macos" || os == "darwin" -> "macos"
            else -> unsupported()
        }
        val arch = when (osArch.trim().lowercase(Locale.ROOT)) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "armv7", "armv7l" -> "armv7"
            else -> unsupported()
        }
        if (arch == "x86" && family != "windows" || arch == "armv7" && family != "linux") unsupported()
        return "$family-$arch"
    }

    private fun unsupported(): Nothing =
        throw UnsatisfiedLinkError("Unsupported OpenCV desktop platform: os.name=$osName, os.arch=$osArch")

    private fun createPrivateDirectory(): Path {
        val root = tempRoot ?: Path.of(System.getProperty("java.io.tmpdir"))
        val posix = Files.getFileStore(root).supportsFileAttributeView("posix")
        val attributes = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rwx------"),
        )) else emptyArray()
        val directory = Files.createTempDirectory(root, "showcase-opencv-4.12.0-", *attributes).toAbsolutePath()
        try {
            if (!posix) {
                val acl = Files.getFileAttributeView(directory, AclFileAttributeView::class.java)
                    ?: throw IOException("Temporary filesystem cannot provide private native extraction")
                // Restrict Windows access before writing any bytes; children inherit this owner-only ACL.
                acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.owner)
                    .setPermissions(*AclEntryPermission.values())
                    .setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT).build())
            }
            return directory
        } catch (failure: Exception) {
            cleanup(directory, null)
            throw failure
        }
    }

    private fun cleanup(directory: Path, library: Path?) {
        // Unix can unlink a loaded library immediately. Windows may retain its loaded DLL until exit.
        val fileRemoved = library == null || deleteIfPossible(library)
        val directoryRemoved = deleteIfPossible(directory)
        if (!fileRemoved || !directoryRemoved) {
            // The JVM processes these in reverse registration order: DLL first, then its directory.
            // Exit deletion is best effort when Windows still holds the native image open.
            deleteOnExitIfPossible(directory)
            if (!fileRemoved && library != null) deleteOnExitIfPossible(library)
        }
    }

    private fun deleteIfPossible(path: Path): Boolean = try {
        Files.deleteIfExists(path)
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun deleteOnExitIfPossible(path: Path) {
        try {
            path.toFile().deleteOnExit()
        } catch (_: SecurityException) {
            // Cleanup must not mask a load failure or turn a successful System.load into a retry.
        }
    }
}
