
import showcase.buildlogic.ComposePlatformsPlugin
import showcase.buildlogic.appleTargets
import showcase.buildlogic.isTvosBuild
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    id("showcase.platforms")
    id("showcase.upstream-sources")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sentry.multiplatform.gradle.plugin)
}
// File-valued script references avoid AGP 9.1 Lint's external KTS analysis crash (b/430991549).
apply(from = file("../version.gradle.kts"))
// Switching compression changes the bundle even when Kotlin sources are unchanged.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>().configureEach {
    inputs.property(
        "showcaseFullJsCompression",
        providers.environmentVariable("SHOWCASE_FULL_JS_COMPRESSION").orElse("false"),
    )
}
apply(from = file("../gradle/version-web-distribution.gradle.kts"))

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

    jvmToolchain(17)
    android {
        namespace = "com.alpha.showcase.common"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 0 } }
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        withHostTest {}
        withDeviceTest {
            targetSdk { version = release(libs.versions.android.targetSdk.get().toInt()) }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    jvm("desktop")
    
    appleTargets(project).forEach { iosTarget ->
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
        // confettiMain is a shared source directory, not a source set: its presets compile against
        // the ConfettiKit binary in nonTvMain and against verified upstream sources in tvosMain.
        if (isTvosBuild) named("tvosMain") {
            kotlin.srcDir("src/confettiMain/kotlin")
        }

        // Material 3, Coil, Navigation and Material icons are added by ComposePlatformsPlugin,
        // because their coordinates differ between the standard and tvOS profiles.
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.async)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            implementation(libs.compose.animation)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.napier)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.serialization.kotlinx.json)
            implementation(libs.okio)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)
            val supabaseBom = project.dependencies.platform(libs.supabase)
            implementation(supabaseBom)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.functions)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.cryptography.random)
            implementation(project(":showcase-api"))
            implementation(project(":ai-model-capabilities"))
            implementation(project(":local-face-detection"))
        }

        // Libraries that do not publish tvOS variants stay on the existing platforms.
        val nonTvMain by creating {
            dependsOn(commonMain.get())
            // Both dependency variants compile the same application presets.
            kotlin.srcDir("src/confettiMain/kotlin")
            dependencies {
                implementation(libs.kim)
                implementation(libs.kstore)
                implementation(libs.compottie)
                implementation(libs.filekit.core)
                implementation(libs.filekit.dialogs)
                implementation(libs.filekit.dialogs.compose)
                implementation(libs.filekit.coil)
                implementation(libs.shimmer.compose.shimmer)
                implementation(libs.confettikit)
            }
        }
        val nonTvTest by creating { dependsOn(commonTest.get()) }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefs)
        }

        getByName("androidDeviceTest").dependencies {
            implementation("androidx.test:runner:1.7.0")
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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

        val zoomableMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.zoomable) }
        }

        val nonWebMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.ktor.network)
            }
        }

        val fileMain by creating {
            dependsOn(nonWebMain)
            dependsOn(nonTvMain)
            dependsOn(zoomableMain)
            dependencies { implementation(libs.oncekmp) }
        }

        val skiaMain by creating {
            dependsOn(commonMain.get())
        }

        // Skia codecs are also available in the JS and Wasm Compose renderers.
        val gifMain by creating {
            dependsOn(commonMain.get())
        }

        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependsOn(fileMain)
            dependencies {
                api(libs.androidx.activity.compose)
                api(libs.androidx.appcompat)
                api(libs.androidx.core.ktx)
                api(libs.android.compose.ui.tooling.preview)

                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.bundles.lottie)
                implementation(libs.coil.gif)
                implementation(libs.android.compose.ui.tooling)
                implementation(libs.kstore.file)
            }
        }

        val desktopMain by getting {
            dependsOn(gifMain)
            dependsOn(skiaMain)
            dependsOn(jvmMain)
            dependsOn(fileMain)
            dependencies {
                implementation(libs.openize.heic)
                implementation(libs.glavo.avif)
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

        val appleMain by getting {
            dependsOn(nonWebMain)
            dependsOn(nonJvmMain)
            dependsOn(skiaMain)
            dependsOn(gifMain)
            dependencies { implementation(libs.ktor.client.darwin) }
        }

        val iosMain by getting{
            dependsOn(fileMain)
            dependencies {
                implementation(libs.ktor.client.ios)
                implementation(libs.kstore.file)
            }
        }

        iosTest.dependencies {
            implementation(libs.compose.ui.test)
        }

        val webMain by getting {
            dependsOn(nonTvMain)
            dependsOn(gifMain)
            dependencies {
                // Our production webpack config uses this plugin explicitly.
                // Newer webpack no longer supplies it as a transitive dependency.
                implementation(devNpm("terser-webpack-plugin", "5.6.1"))
                implementation(npm("showcase-heic", file("heic-web")))
                implementation(npm("pako", "2.1.0"))
                implementation(libs.androidx.sqlite.web)
                implementation(libs.kotlinx.browser)
                implementation(libs.kstore.storage)
                implementation(libs.ktor.client.js)
                implementation(project(":sqliteWasmWorker"))
            }
        }

        listOf("androidHostTest", "desktopTest", "iosTest", "webTest").forEach {
            named(it) { dependsOn(nonTvTest) }
        }

        val jsMain by getting {
            dependsOn(nonJvmMain)
            dependencies {
                implementation(npm("os-browserify", "0.3.0"))
                implementation(npm("path-browserify", "1.0.1"))
            }
        }

        val wasmJsMain by getting {
            dependsOn(zoomableMain)
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

// Keep platform compatibility policy separate from the source-set architecture.
apply<ComposePlatformsPlugin>()

room3 {
    schemaDirectory("$projectDir/schemas")
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
