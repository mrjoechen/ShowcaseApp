import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.kotlin.dsl.implementation
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    id("io.sentry.kotlin.multiplatform.gradle") version "0.11.0"
}
apply(from = "../version.gradle.kts")

//applyKtorWasmWorkaround(libs.versions.ktor.get())

kotlin {

    // https://kotlinlang.org/docs/multiplatform-hierarchy.html#creating-additional-source-sets
    applyDefaultHierarchyTemplate()

    @OptIn(ExperimentalWasmDsl::class)
//    listOf(
//        js(),
////        wasmJs(),
//    ).forEach {
//        it.moduleName = "ShowcaseApp"
//        it.browser {
//            commonWebpackConfig {
//                outputFileName = "ShowcaseApp.js"
//                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
//                    static = (static ?: mutableListOf()).apply {
//                        // Serve sources to debug inside browser
//                        add(project.projectDir.path)
//                    }
//                }
//            }
//        }
//        it.binaries.executable()
//    }

    androidTarget {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
//                    jvmTarget.set(JvmTarget.JVM_1_8)
//                    freeCompilerArgs.add("-Xjdk-release=${JavaVersion.VERSION_1_8}")
                }
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
        iosSimulatorArm64(),
//        tvosX64(),
//        tvosArm64(),
//        tvosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export("io.sentry:sentry-kotlin-multiplatform:0.11.0")
        }
    }

//    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
//        compilerOptions.freeCompilerArgs.addAll(listOf("-Xklib-duplicated-unique-name-strategy=allow-first-with-warning"))
//    }

    sourceSets {

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
            implementation(libs.napier)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.serialization.kotlinx.json)
            implementation(libs.okio)
            implementation(libs.kstore)
            implementation(libs.compottie)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)
            implementation(libs.navigation.compose)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.coil)
            implementation(libs.ktor.network)
            val supabaseBom = project.dependencies.platform(libs.supabase)
            implementation(supabaseBom)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.auth)
            implementation(libs.shimmer.compose.shimmer)
            implementation(libs.confettikit)
            implementation(project(":showcase-api"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefs)
        }

        val jvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // JVM平台通用依赖
            }
        }

        val nonWebMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.sentry.kotlin.multiplatform)
            }
        }

        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependsOn(nonWebMain)
            dependencies {
                api(libs.androidx.activity.compose)
                api(libs.androidx.appcompat)
                api(libs.androidx.core.ktx)
                api(libs.compose.ui.tooling.preview)

                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.bundles.lottie)
                implementation(compose.uiTooling)
                implementation(libs.kstore.file)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependsOn(nonWebMain)
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
                implementation(libs.flatlaf)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kstore.file)
                implementation(libs.appdirs)
            }
        }

        val iosMain by getting{
            dependsOn(nonWebMain)
            dependsOn(nonJvmMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.ktor.client.ios)
                implementation(libs.kstore.file)
            }
        }

        val webMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kstore.storage)
                implementation(libs.ktor.client.js)
            }
        }

//        val jsMain by getting{
//            dependsOn(webMain)
//            dependsOn(nonJvmMain)
//            dependencies {
//                implementation(libs.okio.js)
//            }
//        }

//        val wasmJsMain by getting{
//            dependsOn(webMain)
//            dependsOn(nonJvmMain)
//            dependencies {
//                implementation(npm("uuid", "9.0.0"))
//            }
//        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

    val localProperties = gradleLocalProperties(rootDir, providers)

    val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL")
    val supabaseKey: String = localProperties.getProperty("SUPABASE_ANON_KEY")
    val sentryDsn: String = localProperties.getProperty("SENTRY_DSN")

    require(supabaseUrl.isNotEmpty()) {
        "Register your api SUPABASE_URL place it in local.properties as `SUPABASE_URL`"
    }

    require(supabaseKey.isNotEmpty()) {
        "Register your api SUPABASE_ANON_KEY place it in local.properties as `SUPABASE_ANON_KEY`"
    }

    require(sentryDsn.isNotEmpty()) {
        "Register your api SENTRY_DSN place it in local.properties as `SENTRY_DSN`"
    }

    buildConfigField("SUPABASE_URL", supabaseUrl)
    buildConfigField("SUPABASE_ANON_KEY", supabaseKey)
    buildConfigField("SENTRY_DSN", sentryDsn)

    val versionCode: String = project.extra["versionCode"].toString()
    val versionName: String = project.extra["versionName"].toString()
    val gitHash: String = project.extra["gitHash"].toString()
    val versionHash: String = project.extra["versionHash"].toString()
    val author: String = project.extra["author"].toString()
    val email: String = project.extra["email"].toString()

    buildConfigField("versionCode", versionCode)
    buildConfigField("versionName", versionName)
    buildConfigField("gitHash", gitHash)
    buildConfigField("versionHash", versionHash)
    buildConfigField("author", author)
    buildConfigField("email", email)

    buildConfigField("DEBUG", true)

    println("--------------------------------")
    println("versionCode: $versionCode")
    println("versionName: $versionName")
    println("gitHash: $gitHash")
    println("versionHash: $versionHash")
    println("author: $author")
    println("email: $email")
    println("--------------------------------")
}