import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.tasks.Sync

// Version the complete production payload together: Compose string offsets, chunks,
// workers and WASM must never be loaded from different builds. Development is unchanged.
tasks.withType<Sync>().configureEach {
    if (name !in setOf("jsBrowserDistribution", "wasmJsBrowserDistribution")) return@configureEach

    doLast {
        val distribution = destinationDir
        val index = distribution.resolve("index.html")
        check(index.isFile) { "Production distribution is missing index.html: $distribution" }
        val html = index.readText(Charsets.UTF_8)
        val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
            ?: error("Production index.html must contain a <head> element")
        check(!Regex("<base\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            "Production index.html must not set <base>; the distribution task owns its asset base"
        }
        val releases = distribution.resolve("_showcase")
        check(!releases.exists()) {
            "Reserved _showcase directory already exists; production assets must come from a clean Sync"
        }

        // Finder metadata and modification times must not change the release URL.
        distribution.walkTopDown().filter { it.isFile && it.name == ".DS_Store" }.forEach {
            check(it.delete()) { "Cannot remove distribution metadata: $it" }
        }
        val files = distribution.walkTopDown().filter { it.isFile }
            .sortedBy { it.relativeTo(distribution).invariantSeparatorsPath }.toList()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        files.forEach { file ->
            digest.update(file.relativeTo(distribution).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(file.length()).array())
            file.inputStream().use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }
        val version = digest.digest().joinToString("") { "%02x".format(it) }
        val assets = distribution.listFiles()!!.filter { it != index }
        val release = releases.resolve(version)
        check(release.mkdirs()) { "Cannot create versioned production directory: $release" }
        assets.forEach { Files.move(it.toPath(), release.resolve(it.name).toPath()) }

        // A relative base works at /, /app/, /repo/web/ and their index.html URLs.
        // It also keeps fetch(), font preloads, webpack chunks and worker URLs together.
        index.writeText(
            html.replaceRange(head.range.last + 1, head.range.last + 1, "\n    <base href=\"./_showcase/$version/\">"),
            Charsets.UTF_8,
        )
        logger.lifecycle("Versioned ${name} assets: _showcase/$version/")
    }
}
