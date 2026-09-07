package com.alpha.facedetection.internal

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class DesktopNativeLoaderTest {
    @Test
    fun hostAliasesSelectOnlyTheirOwnPackagedRuntime() {
        val hosts = listOf(
            Triple("Windows 11", "amd64", "windows-x86_64"),
            Triple("Windows 10", "x86_64", "windows-x86_64"),
            Triple("Windows 11", "aarch64", "windows-aarch64"),
            Triple("Windows 10", "i686", "windows-x86"),
            Triple("Linux", "AMD64", "linux-x86_64"),
            Triple("Linux", "x64", "linux-x86_64"),
            Triple("Linux", "arm64", "linux-aarch64"),
            Triple("Linux", "armv7l", "linux-armv7"),
            Triple("Mac OS X", "x86_64", "macos-x86_64"),
            Triple("macOS", "aarch64", "macos-aarch64"),
            Triple("Darwin", "arm64", "macos-aarch64"),
        )
        for ((os, arch, platform) in hosts) withFixture(platform) { fixture ->
            var loaded = false
            fixture.loader(os, arch) { path ->
                assertEquals(fixture.library, Path.of(path).fileName.toString())
                assertContentEquals(PAYLOAD, Files.readAllBytes(Path.of(path)))
                loaded = true
            }.load()
            assertTrue(loaded, "$os / $arch")
            assertEquals(listOf(fixture.manifestPath, fixture.libraryPath), fixture.requested)
            fixture.assertClean()
        }
    }

    @Test
    fun unsupportedHostsFailBeforeReadingResources() {
        for ((os, arch) in listOf("FreeBSD" to "amd64", "Linux" to "riscv64",
            "Mac OS X" to "x86", "Windows CE" to "arm", "Darwin" to "ppc64")) {
            val runtime = DesktopNativeRuntime(os, arch, resource = { fail("Unexpected resource: $it") },
                loadLibrary = { fail("Unexpected native load") })
            val failure = assertFailsWith<UnsatisfiedLinkError> { runtime.load() }
            assertTrue(failure.message.orEmpty().contains("Unsupported"))
        }
    }

    @Test
    fun missingManifestDoesNotFallBackAndCanBeRetried() = withFixture { fixture ->
        val manifest = fixture.resources.remove(fixture.manifestPath)!!
        var loads = 0
        val runtime = fixture.loader { loads++ }
        val failure = assertFailsWith<UnsatisfiedLinkError> { runtime.load() }
        assertTrue(failure.message.orEmpty().contains(fixture.manifestPath))
        assertEquals(listOf(fixture.manifestPath), fixture.requested)
        assertEquals(0, loads)
        fixture.assertClean()
        fixture.resources[fixture.manifestPath] = manifest
        runtime.load()
        runtime.load()
        assertEquals(1, loads)
        fixture.assertClean()
    }

    @Test
    fun missingNativeFailsWithoutTryingOtherArchitectures() = withFixture { fixture ->
        fixture.resources.remove(fixture.libraryPath)
        val failure = assertFailsWith<UnsatisfiedLinkError> { fixture.loader().load() }
        assertTrue(failure.message.orEmpty().contains(fixture.libraryPath))
        assertEquals(listOf(fixture.manifestPath, fixture.libraryPath), fixture.requested)
        fixture.assertClean()
    }

    @Test
    fun invalidMetadataAndUnsafeFilenamesNeverLoad() {
        val invalid = listOf(
            manifest(version = "4.9.0"), manifest(platform = "linux-aarch64"),
            manifest(library = "../opencv_java4120.dll"),
            manifest(library = "C:/tmp/opencv_java4120.dll"),
            manifest(library = "other.dll"), manifest(sha = "0".repeat(63)),
            manifest(sha = "g".repeat(64)), "version=4.12.0\n",
            manifest().replace("sha256=", "ignored="),
            manifest().replace("version=4.12.0", "version=\\uZZZZ"),
        )
        for (metadata in invalid) withFixture { fixture ->
            fixture.resources[fixture.manifestPath] = metadata.toByteArray()
            assertFailsWith<UnsatisfiedLinkError> { fixture.loader().load() }
            assertEquals(listOf(fixture.manifestPath), fixture.requested)
            fixture.assertClean()
        }
    }

    @Test
    fun corruptNativeIsRemovedBeforeAnyLoadAndCorrectedResourceCanRetry() = withFixture { fixture ->
        fixture.resources[fixture.libraryPath] = "tampered".toByteArray()
        var loads = 0
        val runtime = fixture.loader { loads++ }
        val failure = assertFailsWith<UnsatisfiedLinkError> { runtime.load() }
        assertTrue(failure.message.orEmpty().contains("SHA-256"))
        assertEquals(0, loads)
        fixture.assertClean()
        fixture.resources[fixture.libraryPath] = PAYLOAD
        runtime.load()
        assertEquals(1, loads)
        fixture.assertClean()
    }

    @Test
    fun hexadecimalDigestIsCaseInsensitive() = withFixture { fixture ->
        fixture.resources[fixture.manifestPath] = manifest(sha = SHA.uppercase(Locale.ROOT)).toByteArray()
        var loaded = false
        fixture.loader { path ->
            assertContentEquals(PAYLOAD, Files.readAllBytes(Path.of(path)))
            loaded = true
        }.load()
        assertTrue(loaded)
        fixture.assertClean()
    }

    @Test
    fun interruptedExtractionClosesStreamsAndRemovesPartialFile() = withFixture { fixture ->
        var manifestClosed = false
        var nativeClosed = false
        var reads = 0
        val broken = object : InputStream() {
            override fun read(): Int = if (reads++ == 0) 1 else throw IOException("Interrupted copy")
            override fun close() { nativeClosed = true }
        }
        val runtime = DesktopNativeRuntime("Windows 11", "amd64", resource = { path ->
            if (path == fixture.manifestPath) object : ByteArrayInputStream(manifest().toByteArray()) {
                override fun close() { manifestClosed = true; super.close() }
            } else broken
        }, loadLibrary = { fail("Partial native must not load") }, tempRoot = fixture.root)
        assertFailsWith<UnsatisfiedLinkError> { runtime.load() }
        assertTrue(manifestClosed)
        assertTrue(nativeClosed)
        fixture.assertClean()
    }

    @Test
    fun nativeLinkFailureIsPreservedAndRetriedInANewDirectory() = withFixture { fixture ->
        val paths = mutableListOf<Path>()
        val linkFailure = UnsatisfiedLinkError("Missing transitive dependency")
        val runtime = fixture.loader { path ->
            paths.add(Path.of(path))
            if (paths.size == 1) throw linkFailure
        }
        val failure = assertFailsWith<UnsatisfiedLinkError> { runtime.load() }
        assertSame(linkFailure, failure.cause)
        fixture.assertClean()
        runtime.load()
        runtime.load()
        assertEquals(2, paths.size)
        assertNotEquals(paths[0].parent, paths[1].parent)
        fixture.assertClean()
    }

    @Test
    fun initializationIsLazyAndConcurrentCallsLoadExactlyOnce() = withFixture { fixture ->
        val calls = AtomicInteger()
        val enteredLoad = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val runtime = fixture.loader { path ->
            calls.incrementAndGet()
            assertTrue(Path.of(path).isAbsolute)
            assertEquals(fixture.root, Path.of(path).parent.parent)
            if (Files.getFileStore(Path.of(path)).supportsFileAttributeView("posix")) {
                assertEquals(PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(Path.of(path).parent))
            } else {
                val acl = Files.getFileAttributeView(Path.of(path).parent, AclFileAttributeView::class.java)
                assertTrue(acl.acl.isNotEmpty())
                assertTrue(acl.acl.all { it.type() == AclEntryType.ALLOW && it.principal() == acl.owner })
            }
            enteredLoad.countDown()
            assertTrue(releaseLoad.await(10, TimeUnit.SECONDS))
        }
        assertTrue(fixture.requested.isEmpty())
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map { executor.submit(Callable { runtime.load() }) }
            assertTrue(enteredLoad.await(10, TimeUnit.SECONDS))
            assertTrue(futures.none { it.isDone })
            releaseLoad.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, calls.get())
            assertEquals(listOf(fixture.manifestPath, fixture.libraryPath), fixture.requested)
        } finally {
            releaseLoad.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        fixture.assertClean()
    }

    private class Fixture(platform: String) {
        val root: Path = Files.createTempDirectory("desktop-native-test-").toAbsolutePath()
        val library = when {
            platform.startsWith("windows-") -> "opencv_java4120.dll"
            platform.startsWith("macos-") -> "libopencv_java4120.dylib"
            else -> "libopencv_java4120.so"
        }
        val manifestPath = "/com/alpha/facedetection/native/$platform/native.properties"
        val libraryPath = "/com/alpha/facedetection/native/$platform/$library"
        val resources = mutableMapOf(manifestPath to manifest(platform = platform, library = library).toByteArray(),
            libraryPath to PAYLOAD)
        val requested = mutableListOf<String>()

        fun loader(os: String = "Windows 11", arch: String = "amd64",
            load: (String) -> Unit = { fail("Unverified native must not load") }) = DesktopNativeRuntime(
            os, arch, resource = { path -> requested += path; resources[path]?.inputStream() },
            loadLibrary = load, tempRoot = root,
        )

        fun assertClean() = Files.list(root).use { assertFalse(it.findAny().isPresent, "Extraction leaked files") }
    }

    private fun withFixture(platform: String = "windows-x86_64", block: (Fixture) -> Unit) {
        val fixture = Fixture(platform)
        try {
            block(fixture)
        } finally {
            // Only this test's explicitly created root is eligible for recursive cleanup.
            Files.walk(fixture.root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private companion object {
        val PAYLOAD = "abc".toByteArray()
        const val SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        fun manifest(version: String = "4.12.0", platform: String = "windows-x86_64",
            library: String = "opencv_java4120.dll", sha: String = SHA): String =
            "version=$version\nplatform=$platform\nlibrary=$library\nsha256=$sha\n"
    }
}
