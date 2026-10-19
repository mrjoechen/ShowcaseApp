package showcase.buildlogic

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object SourceSelection {
    private const val ICONS = "androidx/compose/material/icons/"
    private const val CONFETTI = "io/github/vinceglb/confettikit/"
    private const val COMPOTTIE = "io/github/alexzhirkevich/compottie/"

    /** Compottie keeps these roots in its output: common expects compile apart from the tvOS actuals. */
    private val COMPOTTIE_SOURCE_SETS = listOf("commonMain", "jvmNativeMain", "skikoMain")

    fun select(archives: List<File>, checksums: Map<String, String>, kind: String, code: List<String>): Map<String, ByteArray> {
        require(archives.isNotEmpty()) { "No upstream source archives resolved for $kind" }
        val root = when (kind) {
            "confetti" -> CONFETTI
            "icons" -> ICONS
            "compottie" -> COMPOTTIE
            else -> error("Unknown source kind: $kind")
        }
        val sourceSets = if (kind == "compottie") COMPOTTIE_SOURCE_SETS else listOf("commonMain")
        val required = when (kind) {
            "icons" -> iconsUsedBy(code)
            "confetti" -> setOf("${CONFETTI}compose/ConfettiKit.kt", "${CONFETTI}core/Party.kt", "${CONFETTI}core/PartySystem.kt")
            else -> setOf(
                "commonMain/${COMPOTTIE}LottiePainter.kt", "commonMain/${COMPOTTIE}LottieComposition.kt",
                "jvmNativeMain/${COMPOTTIE}IoDispatcher.jvmNative.kt", "skikoMain/${COMPOTTIE}LottiePainter.skiko.kt",
            )
        }
        val selected = sortedMapOf<String, ByteArray>()
        archives.forEach { archive ->
            val expected = requireNotNull(checksums[archive.name]) {
                "Unreviewed upstream archive ${archive.name}; follow docs/apple-tv-build.md before updating the source lock"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == expected) { "SHA-256 mismatch for ${archive.name}: expected $expected, got $actual" }
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filter { entry ->
                    !entry.isDirectory && entry.name.endsWith(".kt") && sourceSets.any { entry.name.startsWith("$it/$root") }
                }.forEach { entry ->
                    val name = if (kind == "compottie") entry.name else entry.name.removePrefix("commonMain/")
                    require(name.split('/').none { it == ".." || it == "." || it.isEmpty() } && '\\' !in name) {
                        "Invalid upstream source path: ${entry.name}"
                    }
                    if (kind != "icons" || name in required) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val previous = selected.put(name, bytes)
                        require(previous == null || previous.contentEquals(bytes)) { "Conflicting upstream source: $name" }
                    }
                }
            }
        }
        val missing = required - selected.keys
        require(missing.isEmpty()) { "Missing required $kind sources: ${missing.sorted().joinToString()}" }
        return selected
    }

    private fun iconsUsedBy(code: List<String>): Set<String> {
        val imports = Regex("import\\s+(androidx\\.compose\\.material\\.icons\\.(?:automirrored\\.)?(?:filled|outlined|rounded|sharp|twotone)\\.[A-Z][A-Za-z0-9_]*)")
        val usages = Regex("Icons\\.(AutoMirrored\\.)?(Default|Filled|Outlined|Rounded|Sharp|TwoTone)\\.([A-Za-z0-9_]+)")
        return buildSet {
            add("${ICONS}Icons.kt")
            code.forEach { text ->
                imports.findAll(text).forEach { add(it.groupValues[1].replace('.', '/') + ".kt") }
                usages.findAll(text).forEach {
                    val style = if (it.groupValues[2] == "Default") "filled" else it.groupValues[2].lowercase()
                    val mirrored = if (it.groupValues[1].isEmpty()) "" else "automirrored/"
                    add("$ICONS$mirrored$style/${it.groupValues[3]}.kt")
                }
            }
        }
    }
}
