package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Local
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSourceRepoTest {
    private lateinit var root: Path
    private lateinit var source: Local
    private val repository = RepoManager(
        defaultCacheServiceProvider = { error("Local folders must not use the network cache") },
    )

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("showcase-local-recursive-")
        source = Local(name = "local-recursive", path = root.toString())
        Files.createDirectories(root.resolve("child/grandchild"))
        Files.createDirectories(root.resolve("empty"))
        for (path in listOf("root.jpg", "child/child.jpg", "child/grandchild/deep.jpg", "child/notes.txt")) {
            Files.write(root.resolve(path), byteArrayOf(1, 2, 3))
        }
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun recursiveLoadIncludesAllLevelsEvenWhenFilterRejectsDirectories() = runBlocking {
        val files = loadImages(recursive = true)

        assertEquals(setOf("root.jpg", "child/child.jpg", "child/grandchild/deep.jpg"), relativePaths(files))
        assertTrue(files.all { it.remote == source })
        assertTrue(files.all { it.size == 3L })
    }

    @Test
    fun disablingRecursionListsOnlyTheSelectedFolder() = runBlocking {
        assertEquals(setOf("root.jpg"), relativePaths(loadImages(recursive = false)))
    }

    @Test
    fun togglingRecursionReloadsTheSameSourceWithoutClearingCaches() = runBlocking {
        assertEquals(null, repository.ensureCacheReady(source, recursive = false).getOrThrow())
        assertEquals(setOf("root.jpg"), relativePaths(loadImages(recursive = false)))

        Files.write(root.resolve("child/new.jpg"), byteArrayOf(4))
        assertEquals(null, repository.ensureCacheReady(source, recursive = true).getOrThrow())
        assertEquals(
            setOf("root.jpg", "child/child.jpg", "child/grandchild/deep.jpg", "child/new.jpg"),
            relativePaths(loadImages(recursive = true)),
        )
        assertEquals(setOf("root.jpg"), relativePaths(loadImages(recursive = false)))
    }

    @Test
    fun unfilteredListingPreservesShallowDirectoriesAndFlattensRecursiveFiles() = runBlocking {
        val shallow = repository.getItems(source, recursive = false).getOrThrow().map { it as NetworkFile }
        assertEquals(setOf("root.jpg", "child", "empty"), relativePaths(shallow))
        assertEquals(setOf("child", "empty"), relativePaths(shallow.filter { it.isDirectory }))

        val recursive = repository.getItems(source, recursive = true).getOrThrow().map { it as NetworkFile }
        assertEquals(
            setOf("root.jpg", "child/child.jpg", "child/grandchild/deep.jpg", "child/notes.txt"),
            relativePaths(recursive),
        )
        assertTrue(recursive.none { it.isDirectory })
    }

    @Test
    fun emptyFolderLoadsSuccessfullyInBothModes() = runBlocking {
        val emptySource = Local(name = "empty", path = root.resolve("empty").toString())
        for (recursive in listOf(false, true)) {
            assertEquals(emptyList(), repository.getItems(emptySource, recursive).getOrThrow())
        }
    }

    @Test
    fun directoryLinkToAncestorDoesNotLoopOrDuplicateFiles() = runBlocking {
        val link = root.resolve("child/back-to-root")
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), root.toString())
                    .redirectErrorStream(true).start()
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Creating a test junction timed out")
                assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
            } else {
                Files.createSymbolicLink(link, root)
            }
            val files = withTimeout(5_000) { loadImages(recursive = true) }
            assertEquals(setOf("root.jpg", "child/child.jpg", "child/grandchild/deep.jpg"), relativePaths(files))
            assertEquals(3, files.size)
        } finally {
            // Remove the link itself before recursive fixture cleanup, especially for Windows junctions.
            Files.deleteIfExists(link)
        }
    }

    private suspend fun loadImages(recursive: Boolean): List<NetworkFile> =
        repository.getItems(source, recursive) {
            it is NetworkFile && !it.isDirectory && it.fileName.endsWith(".jpg")
        }.getOrThrow().map { it as NetworkFile }

    private fun relativePaths(files: List<NetworkFile>): Set<String> = files.map {
        root.relativize(Path.of(it.path)).toString().replace('\\', '/')
    }.toSet()
}
