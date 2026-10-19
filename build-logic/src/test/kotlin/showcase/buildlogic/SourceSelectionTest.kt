package showcase.buildlogic

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val confettiRoot = "io/github/vinceglb/confettikit/"
    private val iconsRoot = "androidx/compose/material/icons/"
    private val compottieRoot = "io/github/alexzhirkevich/compottie/"

    private fun archive(name: String, entries: Map<String, String>): File = temporary.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun lock(vararg files: File) = files.associate {
        it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun confetti() = mapOf(
        "commonMain/${confettiRoot}compose/ConfettiKit.kt" to "// Copyright\r\ncompose",
        "commonMain/${confettiRoot}core/PartySystem.kt" to "core",
        "commonMain/${confettiRoot}core/Party.kt" to "party",
    )

    @Test fun preservesBytesAndExcludesOtherSourceSets() {
        val file = archive("confetti.jar", confetti() + ("jvmMain/Other.kt" to "ignore"))
        val selected = SourceSelection.select(listOf(file), lock(file), "confetti", emptyList())
        assertEquals(3, selected.size)
        assertArrayEquals("// Copyright\r\ncompose".toByteArray(), selected["${confettiRoot}compose/ConfettiKit.kt"])
    }

    @Test fun rejectsUnreviewedOrChangedArchives() {
        val file = archive("confetti.jar", confetti())
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(listOf(file), emptyMap(), "confetti", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(listOf(file), mapOf(file.name to "0".repeat(64)), "confetti", emptyList()) }
    }

    @Test fun rejectsMissingArchivesAndMissingRequiredSources() {
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(emptyList(), emptyMap(), "confetti", emptyList()) }
        val file = archive("empty.jar", mapOf("newLayout/ConfettiKit.kt" to "changed"))
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(listOf(file), lock(file), "confetti", emptyList()) }
    }

    @Test fun selectsExplicitAliasedAndWildcardIconUsages() {
        val names = listOf("Icons", "filled/Home", "outlined/Settings", "automirrored/filled/ArrowBack", "filled/Unused")
        val file = archive("icons.jar", names.associate { "commonMain/$iconsRoot$it.kt" to it })
        val selected = SourceSelection.select(listOf(file), lock(file), "icons", listOf("""
            import androidx.compose.material.icons.outlined.Settings as SettingsIcon
            import androidx.compose.material.icons.filled.*
            val home = Icons.Default.Home
            val back = Icons.AutoMirrored.Filled.ArrowBack
        """))
        assertEquals(names.dropLast(1).map { "$iconsRoot$it.kt" }.toSet(), selected.keys)
    }

    @Test fun rejectsReferencedIconsMissingFromUpstream() {
        val file = archive("icons.jar", mapOf("commonMain/${iconsRoot}Icons.kt" to "helper"))
        val error = assertThrows(IllegalArgumentException::class.java) {
            SourceSelection.select(listOf(file), lock(file), "icons", listOf("Icons.Filled.Missing"))
        }
        assertTrue(error.message!!.contains("filled/Missing.kt"))
    }

    @Test fun permitsIdenticalHelpersButRejectsConflictingDuplicates() {
        val first = archive("first.jar", confetti())
        val same = archive("same.jar", confetti())
        assertEquals(3, SourceSelection.select(listOf(first, same), lock(first, same), "confetti", emptyList()).size)
        val changed = archive("changed.jar", confetti().mapValues { "different" })
        assertThrows(IllegalArgumentException::class.java) {
            SourceSelection.select(listOf(first, changed), lock(first, changed), "confetti", emptyList())
        }
    }

    private fun compottie() = listOf(
        "commonMain/${compottieRoot}LottiePainter.kt", "commonMain/${compottieRoot}LottieComposition.kt",
        "jvmNativeMain/${compottieRoot}IoDispatcher.jvmNative.kt", "skikoMain/${compottieRoot}LottiePainter.skiko.kt",
    ).associateWith { it }

    @Test fun keepsCompottieActualRootsAndExcludesWebSources() {
        val file = archive("compottie.jar", compottie() + ("webMain/${compottieRoot}IoDispatcher.web.kt" to "web"))
        val selected = SourceSelection.select(listOf(file), lock(file), "compottie", emptyList())
        assertEquals(compottie().keys, selected.keys)
    }

    @Test fun rejectsCompottieWithoutSkikoActuals() {
        val file = archive("compottie.jar", compottie().filterKeys { !it.startsWith("skikoMain/") })
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(listOf(file), lock(file), "compottie", emptyList()) }
    }

    @Test fun rejectsTraversalWithinSourceRoot() {
        val file = archive("unsafe.jar", confetti() + ("commonMain/${confettiRoot}../../escape.kt" to "bad"))
        assertThrows(IllegalArgumentException::class.java) { SourceSelection.select(listOf(file), lock(file), "confetti", emptyList()) }
    }
}
