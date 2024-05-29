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
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}



// 获取 Git 提交数
fun retrieveGitCommitCount(): Int {
    return try {
        val process = Runtime.getRuntime().exec("git rev-list --count HEAD")
        val output = process.inputStream.reader(Charsets.UTF_8).readText()
        output.trim().toInt()
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }
}

fun retrieveGitHash(): String {
    return try {
        val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
        val output = process.inputStream.reader(Charsets.UTF_8).readText()
        output.trim()
    } catch (e: Exception) {
        e.printStackTrace()
        "error"
    }
}
val gitCommitCount = retrieveGitCommitCount()
val gitHash = retrieveGitHash()
val versionCode = gitCommitCount + 10000
val versionName = findProperty("showcase.versionName") as String

project.extra["gitCommitCount"] = gitCommitCount
project.extra["gitHash"] = gitHash
project.extra["versionCode"] = versionCode
project.extra["versionName"] = versionName

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
        compilations.all {
            kotlinOptions {
                jvmTarget = "${JavaVersion.VERSION_1_8}"
                freeCompilerArgs += "-Xjdk-release=${JavaVersion.VERSION_1_8}"
            }
        }

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
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.flatlaf)
            }
        }
        
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kstore)
            implementation(libs.kstore.file)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
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

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.kstore)
            implementation(libs.kstore.file)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kstore)
            implementation(libs.kstore.file)
            implementation("net.harawata:appdirs:1.2.2")
        }
    }
}



val Project.gitHash: String
    get() = project.extra["gitHash"] as String
val date = SimpleDateFormat("yyyyMMddHHmm")
val formattedDate: String = date.format(Calendar.getInstance().time)

android {
    namespace = "com.alpha.showcase"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.alpha.showcase"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = project.extra["versionCode"] as Int
        versionName = project.extra["versionName"] as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty(
            "archivesBaseName",
            "showcase-android-$versionCode.${gitHash}($versionName)${formattedDate}"
        )
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            (this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl)?.outputFileName =
                "showcase-android.${versionName}.${gitHash}_${versionCode}-${formattedDate}-${name}.apk"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = true
        }
    }

    packaging {

        jniLibs {
            useLegacyPackaging = true
        }

        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        all {
            jniLibs.srcDirs(arrayOf("lib"))
        }
    }

    bundle {
        language {
            // Specify a list of split dimensions for language splits
            enableSplit = true
        }
        density {
            // Specify a list of split dimensions for density splits
            enableSplit = true
        }
        abi {
            // Specify a list of split dimensions for ABI splits
            enableSplit = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        jvmToolchain(17)
    }

    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
        project.version = project.extra["versionCode"].toString()
        mainClass = "Showcase"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.alpha.showcase"
            packageVersion = project.extra["versionName"] as String
            version = project.version
            description = "Showcase App"
            copyright = "© 2024 Joe Chen. All rights reserved."
            vendor = "GitHub"
            buildTypes.release.proguard {
                configurationFiles.from("rules.pro")
            }
        }
    }
}

compose.experimental {
    web.application {}
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
    packageName("com.alpha.showcase")

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

    buildConfigField("versionCode", versionCode)
    buildConfigField("versionName", versionName)
    buildConfigField("gitHash", gitHash)

}