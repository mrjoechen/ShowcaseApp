package showcase.buildlogic

import io.sentry.kotlin.multiplatform.gradle.SentryExtension
import java.util.Properties
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

/**
 * Applied after the application's source sets exist. Owns the commonMain dependencies whose
 * coordinates differ between the standard and tvOS profiles (Material 3, Coil, Navigation,
 * Material icons) and the tvOS-only build wiring. Profile-independent dependencies stay in
 * composeApp/build.gradle.kts.
 */
class ComposePlatformsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Settings redirects the entire invocation. Ordinary builds retain official catalog aliases.
        val tvosEnabled = isTvosBuild
        val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val tvosVersions = Properties().apply {
            if (tvosEnabled) rootProject.file("gradle/tvos.versions.properties").inputStream().use { load(it) }
        }
        fun dependency(alias: String, module: String, versionKey: String): Any =
            if (tvosEnabled) "$module:${tvosVersions.getProperty(versionKey) ?: error("Missing tvOS version: $versionKey")}"
            else catalog.findLibrary(alias).get()

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(dependency("compose-material3", "org.jetbrains.compose.material3:material3", "material3"))
                implementation(dependency("coil", "io.coil-kt.coil3:coil-compose", "coil"))
                implementation(dependency("coil-network-ktor", "io.coil-kt.coil3:coil-network-ktor3", "coil"))
                implementation(dependency("coil-svg", "io.coil-kt.coil3:coil-svg", "coil"))
                implementation(dependency("navigation-compose", "org.jetbrains.androidx.navigation:navigation-compose", "navigation"))
                if (!tvosEnabled) implementation(catalog.findLibrary("material-icons-extended").get())
            }
            if (tvosEnabled) {
                val sources = project.extensions.getByType<UpstreamSources>()
                val icons = sources.icons(fileTree("src") { include("**/*.kt") })
                val confetti = sources.confetti()
                sourceSets.getByName("commonMain").kotlin.srcDir(icons.flatMap { it.outputDirectory })
                sourceSets.getByName("tvosMain").kotlin.srcDir(confetti.flatMap { it.outputDirectory })
            }
        }

        if (tvosEnabled) {
            listOf("kspTvosArm64", "kspTvosSimulatorArm64").forEach {
                dependencies.add(it, catalog.findLibrary("androidx-room-compiler").get())
            }
            // Sentry discovery does not inherit Xcode's -derivedDataPath. A static ComposeApp
            // framework needs the static Sentry slice, resolved by Xcode's Swift package manager.
            val artifact = "SourcePackages/artifacts/sentry-cocoa/Sentry/Sentry.xcframework"
            val derivedData = providers.environmentVariable("BUILD_DIR").orNull
                ?.substringBeforeLast("/Build/", "")?.takeIf { it.isNotEmpty() }
            val explicitFramework = providers.gradleProperty("showcase.sentryCocoaFramework").orNull
            val framework = explicitFramework?.let(::file)
                ?: derivedData?.let { file("$it/$artifact") }
                ?: rootProject.layout.buildDirectory.dir("tvos-xcode/$artifact").get().asFile
            require(explicitFramework == null || framework.isDirectory) {
                "showcase.sentryCocoaFramework does not point to a directory: $framework"
            }
            if (framework.isDirectory) {
                extensions.configure<SentryExtension> { linker.frameworkPath.set(framework.absolutePath) }
            }
        }
    }
}
