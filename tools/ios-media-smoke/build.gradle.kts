plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidLibrary) apply false
}
kotlin {
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation("io.github.alexzhirkevich:compottie:${providers.gradleProperty("compottieVersion").getOrElse(libs.versions.compottie.get())}")
            implementation(project(":local-face-detection"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
        }
        val iosSimulatorArm64Test by getting {
            kotlin.srcDir("../../composeApp/src/iosTest/kotlin/com/alpha/showcase/common/media")
        }
    }
}
