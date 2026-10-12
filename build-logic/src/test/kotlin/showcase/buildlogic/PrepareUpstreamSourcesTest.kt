package showcase.buildlogic

import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrepareUpstreamSourcesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun successfulRegenerationRemovesStaleFilesAndFailedValidationPreservesOutput() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.root).build()
        val archive = temporary.newFile("icons.jar")
        val root = "androidx/compose/material/icons/"
        ZipOutputStream(archive.outputStream()).use { zip ->
            listOf("Icons", "filled/Home", "filled/Settings").forEach {
                zip.putNextEntry(ZipEntry("commonMain/$root$it.kt"))
                zip.write("// Original bytes: $it\r\n".toByteArray())
                zip.closeEntry()
            }
        }
        val checksum = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        val lock = temporary.newFile("sources.properties").apply { writeText("${archive.name}=$checksum") }
        val code = temporary.newFile("App.kt").apply { writeText("Icons.Filled.Home") }
        val task = project.tasks.register("prepare", PrepareUpstreamSources::class.java).get().apply {
            archives.from(archive)
            applicationSources.from(code)
            sourceLock.set(lock)
            kind.set("icons")
            outputDirectory.set(project.layout.buildDirectory.dir("generated"))
        }
        task.prepare()
        val output = task.outputDirectory.get().asFile
        assertTrue(output.resolve("${root}filled/Home.kt").isFile)
        code.writeText("Icons.Filled.Settings")
        task.prepare()
        assertFalse(output.resolve("${root}filled/Home.kt").exists())
        val current = output.resolve("${root}filled/Settings.kt")
        assertEquals("// Original bytes: filled/Settings\r\n", current.readText())
        lock.writeText("${archive.name}=${"0".repeat(64)}")
        assertThrows(IllegalArgumentException::class.java) { task.prepare() }
        assertEquals("// Original bytes: filled/Settings\r\n", current.readText())
    }
}
