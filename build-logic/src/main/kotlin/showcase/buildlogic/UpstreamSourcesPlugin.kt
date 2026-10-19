package showcase.buildlogic

import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider

class UpstreamSourcesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("upstreamSources", UpstreamSources::class.java, project)
    }
}

/** The application and standalone verifier share one source acquisition path. No Kotlin plugin dependency. */
abstract class UpstreamSources @Inject constructor(private val project: Project) {
    val repositoryRoot: DirectoryProperty = project.objects.directoryProperty().convention(project.rootProject.layout.projectDirectory)
    private val versions get() = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

    fun confetti(): TaskProvider<PrepareUpstreamSources> = register(
        "Confetti", "confetti", listOf("io.github.vinceglb:confettikit:${versions.findVersion("confettikit").get()}:sources@jar"),
    )

    /** Keeps Compottie's source-set roots: common expects need the Skiko and native actuals. */
    fun compottie(): TaskProvider<PrepareUpstreamSources> {
        val version = versions.findVersion("compottie").get()
        return register("Compottie", "compottie", listOf(
            "io.github.alexzhirkevich:compottie-core:$version:sources@jar",
            "io.github.alexzhirkevich:compottie:$version:sources@jar",
        ))
    }

    fun icons(sources: FileCollection): TaskProvider<PrepareUpstreamSources> {
        val version = versions.findVersion("materialIconsExtended").get()
        return register("Icons", "icons", listOf(
            "org.jetbrains.compose.material:material-icons-core:$version:sources@jar",
            "org.jetbrains.compose.material:material-icons-extended:$version:sources@jar",
        )).also { task -> task.configure { applicationSources.from(sources) } }
    }

    private fun register(label: String, sourceKind: String, coordinates: List<String>): TaskProvider<PrepareUpstreamSources> {
        val configuration = project.configurations.create("tvos${label}Sources") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            description = "Pinned upstream $label source archives, never added as binaries"
        }
        coordinates.forEach { project.dependencies.add(configuration.name, it) }
        return project.tasks.register("prepareTvos$label", PrepareUpstreamSources::class.java) {
            group = "build setup"
            description = "Validate and extract upstream $label sources"
            kind.set(sourceKind)
            archives.from(configuration)
            sourceLock.set(repositoryRoot.file("gradle/upstream-sources.properties"))
            outputDirectory.set(project.layout.buildDirectory.dir("generated/tvos$label"))
        }
    }
}
