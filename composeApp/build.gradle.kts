
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sentry.multiplatform.gradle.plugin)
}
apply(from = "../version.gradle.kts")
// Switching compression changes the bundle even when Kotlin sources are unchanged.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>().configureEach {
    inputs.property(
        "showcaseFullJsCompression",
        providers.environmentVariable("SHOWCASE_FULL_JS_COMPRESSION").orElse("false"),
    )
}
apply(from = "../gradle/version-web-distribution.gradle.kts")

//applyKtorWasmWorkaround(libs.versions.ktor.get())

kotlin {

    // https://kotlinlang.org/docs/multiplatform-hierarchy.html#creating-additional-source-sets
    applyDefaultHierarchyTemplate()

    @OptIn(ExperimentalWasmDsl::class)
    listOf(
        js(),
        wasmJs(),
    ).forEach {
        it.outputModuleName = "ShowcaseApp"
        it.browser {
            commonWebpackConfig {
                outputFileName = "ShowcaseApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(project.projectDir.path)
                }
            }
        }
        it.binaries.executable()
    }

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
    }
    
    jvm("desktop")
    
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
//        tvosArm64(),
//        tvosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.alpha.showcase.composeapp")
        }
    }

//    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
//        compilerOptions.freeCompilerArgs.addAll(listOf("-Xklib-duplicated-unique-name-strategy=allow-first-with-warning"))
//    }

    sourceSets {

        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.async)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.animation)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.material.icons.extended)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
            implementation(libs.coil.svg)
            implementation(libs.napier)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.runtime.compose)
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
            val supabaseBom = project.dependencies.platform(libs.supabase)
            implementation(supabaseBom)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.auth)
            implementation(libs.shimmer.compose.shimmer)
            implementation(libs.confettikit)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.cryptography.random)
            implementation(project(":showcase-api"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefs)
        }

        val jvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // JVM平台通用依赖
                implementation(libs.smbj)
                implementation(libs.commons.net)
                implementation(libs.jsch)
                implementation("com.rapid7.client:dcerpc:0.12.13")
            }
        }

        val nonWebMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.ktor.network)
                implementation(libs.oncekmp)
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
                api(libs.android.compose.ui.tooling.preview)

                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.bundles.lottie)
                implementation(libs.coil.gif)
                implementation(libs.android.compose.ui.tooling)
                implementation(libs.kstore.file)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependsOn(nonWebMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kstore.file)
                implementation(libs.appdirs)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport.native.unixsocket)
            }
        }

        val desktopTest by getting {
            dependencies {
                // Real schema-driven migration tests (MigrationTestHelper) run on
                // the desktop JVM against the JSONs in composeApp/schemas.
                implementation(libs.androidx.room.testing)
                implementation(libs.compose.ui.test)
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

        val webMain by getting {
            dependencies {
                implementation(libs.androidx.sqlite.web)
                implementation(libs.kotlinx.browser)
                implementation(libs.kstore.storage)
                implementation(libs.ktor.client.js)
                implementation(project(":sqliteWasmWorker"))
            }
        }

        val jsMain by getting {
            dependsOn(nonJvmMain)
            dependencies {
                implementation(npm("os-browserify", "0.3.0"))
                implementation(npm("path-browserify", "1.0.1"))
            }
        }

        val wasmJsMain by getting {
            dependsOn(nonJvmMain)
            dependencies {
                implementation(npm("os-browserify", "0.3.0"))
                implementation(npm("path-browserify", "1.0.1"))
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJs", libs.androidx.room.compiler)
    add("kspWasmJs", libs.androidx.room.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
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

    println("--------------------------------")
    println("versionCode: $versionCode")
    println("versionName: $versionName")
    println("gitHash: $gitHash")
    println("versionHash: $versionHash")
    println("author: $author")
    println("email: $email")
    println("--------------------------------")
}
