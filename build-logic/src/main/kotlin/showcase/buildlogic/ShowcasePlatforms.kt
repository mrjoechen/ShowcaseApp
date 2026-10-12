package showcase.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/** Owned by scripts/gradle-platform.sh; settings.gradle.kts reads it before build-logic is available. */
const val TVOS_PROFILE_PROPERTY = "showcase.tvos"

/** True only for the opt-in tvOS profile, which redirects the whole Gradle invocation. */
val Project.isTvosBuild: Boolean
    get() = providers.gradleProperty(TVOS_PROFILE_PROPERTY).orNull == "true"

/** tvOS targets for the opt-in profile; empty for standard builds. */
fun KotlinMultiplatformExtension.tvosTargets(project: Project): List<KotlinNativeTarget> =
    if (project.isTvosBuild) listOf(tvosArm64(), tvosSimulatorArm64()) else emptyList()

/** iOS targets always, plus tvOS targets in the opt-in profile. */
fun KotlinMultiplatformExtension.appleTargets(project: Project): List<KotlinNativeTarget> =
    listOf(iosArm64(), iosSimulatorArm64()) + tvosTargets(project)

/** Puts the platform helpers above on a module's build script classpath. */
class ShowcasePlatformsPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}
