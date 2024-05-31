import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.text.SimpleDateFormat
import java.util.Calendar

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}
apply(from = "../version.gradle.kts")

applyKtorWasmWorkaround(libs.versions.ktor.get())

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "ShowcaseApp"
        browser {
            commonWebpackConfig {
                outputFileName = "ShowcaseApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }
    
    androidTarget {
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant {
            sourceSetTree.set(KotlinSourceSetTree.test)
            dependencies {
                debugImplementation(libs.androidx.testManifest)
                implementation(libs.androidx.junit4)
            }
        }
    }
    
    jvm("desktop")
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ShowcaseApp"
            isStatic = true
        }
    }
    
    sourceSets {

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.napier)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
            implementation(libs.napier)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.serialization.kotlinx.json)
            implementation(project(":showcase-api"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            api(libs.androidx.appcompat)
            api(libs.androidx.core.ktx)

            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.bundles.lottie)
            implementation(compose.uiTooling)
            implementation(libs.kstore)
            implementation(libs.kstore.file)

        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.kstore)
            implementation(libs.kstore.file)
        }


        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
                implementation(libs.flatlaf)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kstore)
                implementation(libs.kstore.file)
                implementation(libs.appdirs)
            }
        }
    }
}


android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "com.alpha.showcase.common"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testOptions.targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

// https://youtrack.jetbrains.com/issue/KTOR-5587
fun Project.applyKtorWasmWorkaround(version: String) {
    configurations.all {
        if (name.startsWith("wasmJs")) {
            resolutionStrategy.eachDependency {
                if (requested.group.startsWith("io.ktor") &&
                    requested.name.startsWith("ktor-client-")) {
                    useVersion(version)
                }
            }
        }
    }
}


buildConfig {
    // BuildConfig configuration here.
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
    useKotlinOutput { topLevelConstants = true }
    packageName("com.alpha.showcase.common")

    val localProperties = gradleLocalProperties(rootDir)
    val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY")
    require(tmdbApiKey.isNotEmpty()) {
        "Register your api TMDB_API_KEY place it in local.properties as `TMDB_API_KEY`"
    }

    val pexelsApiKey: String = localProperties.getProperty("PEXELS_API_KEY")
    require(pexelsApiKey.isNotEmpty()) {
        "Register your api PEXELS_API_KEY place it in local.properties as `PEXELS_API_KEY`"
    }

    val unsplashApiKey: String = localProperties.getProperty("UNSPLASH_API_KEY")
    require(unsplashApiKey.isNotEmpty()) {
        "Register your api UNSPLASH_API_KEY place it in local.properties as `UNSPLASH_API_KEY`"
    }


    buildConfigField("PEXELS_API_KEY", pexelsApiKey)
    buildConfigField("UNSPLASH_API_KEY", unsplashApiKey)
    buildConfigField("TMDB_API_KEY", tmdbApiKey)


    val versionCode: String = project.extra["versionCode"].toString()
    val versionName: String = project.extra["versionName"].toString()
    val gitHash: String = project.extra["gitHash"].toString()
    val versionHash: String = project.extra["versionHash"].toString()

    buildConfigField("versionCode", versionCode)
    buildConfigField("versionName", versionName)
    buildConfigField("gitHash", gitHash)
    buildConfigField("versionHash", versionHash)
    println("--------------------------------")
    println("versionCode: $versionCode")
    println("versionName: $versionName")
    println("gitHash: $gitHash")
    println("versionHash: $versionHash")
    println("--------------------------------")
}