package showcase.buildlogic

import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/** Validates all inputs before replacing generated sources, preserving upstream bytes and notices. */
@CacheableTask
abstract class PrepareUpstreamSources : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val archives: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val applicationSources: ConfigurableFileCollection

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceLock: RegularFileProperty

    @get:Input abstract val kind: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        val properties = Properties().apply { sourceLock.get().asFile.inputStream().use { load(it) } }
        val selected = SourceSelection.select(
            archives.files.sortedBy { it.name },
            properties.stringPropertyNames().associateWith { properties.getProperty(it) },
            kind.get(),
            applicationSources.files.sorted().map { it.readText() },
        )
        val staging = temporaryDir.resolve("validated")
        fileSystem.delete { delete(staging) }
        selected.forEach { (name, bytes) ->
            staging.resolve(name).apply { parentFile.mkdirs(); writeBytes(bytes) }
        }
        fileSystem.sync { from(staging); into(outputDirectory) }
        logger.lifecycle("Validated {} upstream {} source files", selected.size, kind.get())
    }
}
