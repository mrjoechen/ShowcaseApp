import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64 as JavaBase64
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

val isMacHost = providers.systemProperty("os.name").map { it.startsWith("Mac", ignoreCase = true) }
val desktopNativeOs = providers.systemProperty("os.name").get().lowercase().let {
    when {
        it.startsWith("windows") -> "windows"
        it.startsWith("mac") -> "macos"
        it.startsWith("linux") -> "linux"
        else -> "unsupported"
    }
}
val desktopNativeArch = providers.systemProperty("os.arch").get().lowercase().let {
    when (it) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> "unsupported"
    }
}
val desktopNativePlatform = "$desktopNativeOs-$desktopNativeArch"
val desktopOpenCvArtifact = "opencv-yunet-desktop-4.12.0-1"
val desktopOpenCvBindings = file("desktop-runtime/$desktopOpenCvArtifact.jar")
val desktopOpenCvNative = file("desktop-runtime/$desktopOpenCvArtifact-$desktopNativePlatform.jar")

// Prebuilt inputs only: never compile/download OpenCV while importing the Gradle model.
val verifyDesktopOpenCv by tasks.registering {
    group = "verification"
    description = "Checks that the selected desktop OpenCV package contains exactly one target native."
    doLast {
        check(desktopOpenCvBindings.isFile && desktopOpenCvNative.isFile) {
            "Missing trimmed OpenCV for $desktopNativePlatform. Build it on that host with " +
                "python local-face-detection/scripts/build_desktop.py or install the matching CI artifact. See local-face-detection/DESKTOP.md."
        }
        ZipFile(desktopOpenCvNative).use { archive ->
            val natives = archive.entries().asSequence().map { it.name }
                .filter { it.endsWith(".dll") || it.endsWith(".so") || it.endsWith(".dylib") }.toList()
            check(natives.size == 1 && natives.single().startsWith("com/alpha/facedetection/native/$desktopNativePlatform/")) {
                "OpenCV native package must contain only $desktopNativePlatform: $natives"
            }
        }
    }
}
tasks.matching { it.name in setOf("compileKotlinDesktop", "desktopJar", "desktopTest") }.configureEach {
    dependsOn(verifyDesktopOpenCv)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "com.alpha.facedetection"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 0 } }
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Android-KMP also gates device-test assets on this flag.
        androidResources.enable = true
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        withHostTest {}
        withDeviceTest {
            targetSdk { version = release(libs.versions.android.targetSdk.get().toInt()) }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        optimization.consumerKeepRules.apply {
            publish = true
            file("consumer-rules.pro")
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val nativeDirectory = layout.buildDirectory.dir("ios-native/${target.name}")
        val buildNative = tasks.register<Exec>("buildFaceDetection${target.name.replaceFirstChar(Char::uppercaseChar)}") {
            group = "build"
            description = "Builds the bundled YuNet bridge for ${target.name} using Xcode."
            // IDE import can schedule dependencies of disabled C interop tasks on other hosts.
            onlyIf("iOS native libraries require macOS and Xcode") { isMacHost.get() }
            inputs.files(
                "scripts/build_ios.py",
                "src/iosMain/cpp/FaceDetectionBridge.cpp",
                "src/nativeInterop/cinterop/FaceDetectionBridge.h",
                "src/jvmSharedMain/resources/com/alpha/facedetection/models/face_detection_yunet_2026may.onnx",
            )
            outputs.files(nativeDirectory.map { it.file("libShowcaseFaceDetection.a") },
                nativeDirectory.map { it.file("libopencv2.a") })
            commandLine("python3", file("scripts/build_ios.py").absolutePath,
                "--target", target.name, "--output", nativeDirectory.get().asFile.absolutePath)
        }
        val interop = target.compilations.getByName("main").cinterops.create("faceDetection") {
            definitionFile.set(file("src/nativeInterop/cinterop/faceDetection.def"))
            includeDirs("src/nativeInterop/cinterop")
            extraOpts("-libraryPath", nativeDirectory.get().asFile.absolutePath)
        }
        tasks.named(interop.interopProcessingTaskName).configure {
            dependsOn(buildNative)
            inputs.files(buildNative)
            inputs.file("src/nativeInterop/cinterop/FaceDetectionBridge.h")
        }
    }
    js(IR) { browser(); nodejs() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compile against the platform's bindings; OpenCV is not a common metadata dependency.
        androidMain {
            kotlin.srcDir("src/jvmSharedMain/kotlin")
            dependencies { implementation("com.alpha.thirdparty:opencv-yunet:4.12.0-r28") }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmSharedMain/kotlin")
            resources.srcDir("src/jvmSharedMain/resources")
            dependencies {
                implementation(files(desktopOpenCvBindings))
                runtimeOnly(files(desktopOpenCvNative))
            }
        }
        named("androidHostTest") {
            kotlin.srcDir("src/jvmSharedTest/kotlin")
            dependencies { implementation(kotlin("test-junit")) }
        }
        val desktopTest by getting {
            kotlin.srcDir("src/jvmSharedTest/kotlin")
            dependencies { implementation(kotlin("test-junit")) }
        }
        iosTest {
            kotlin.srcDir(layout.buildDirectory.dir("generated/iosFaceTestFixtures"))
        }
        named("androidDeviceTest") {
            kotlin.srcDir("src/androidInstrumentedTest/kotlin")
            dependencies {
                implementation(libs.androidx.test.junit)
                implementation("androidx.test:runner:1.7.0")
            }
        }
    }
}

val generateIosFaceTestFixtures by tasks.registering {
    val face = file("src/desktopTest/resources/opencv/lena.jpg")
    val blank = file("src/iosTest/resources/blank.png")
    val blankJpeg = file("src/iosTest/resources/blank.jpg")
    val destination = layout.buildDirectory.dir("generated/iosFaceTestFixtures")
    inputs.files(face, blank, blankJpeg)
    outputs.dir(destination)
    doLast {
        fun encoded(file: File): String = JavaBase64.getEncoder().encodeToString(file.readBytes())
            .chunked(4096).joinToString(",\n") { "\"$it\"" }
        val output = destination.get().file("com/alpha/facedetection/FaceTestFixtures.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            package com.alpha.facedetection
            import kotlin.io.encoding.Base64
            internal object FaceTestFixtures {
                val face: ByteArray get() = Base64.decode(listOf(${encoded(face)}).joinToString(""))
                val blank: ByteArray get() = Base64.decode(listOf(${encoded(blank)}).joinToString(""))
                val blankJpeg: ByteArray get() = Base64.decode(listOf(${encoded(blankJpeg)}).joinToString(""))
            }
        """.trimIndent())
    }
}
tasks.matching { it.name == "compileTestKotlinIosArm64" || it.name == "compileTestKotlinIosSimulatorArm64" }
    .configureEach { dependsOn(generateIosFaceTestFixtures) }

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addStaticSourceDirectory("src/jvmSharedMain/resources")
        variant.deviceTests.values.forEach { test ->
            requireNotNull(test.sources.assets) { "Device-test fixtures require Android resources" }
                .addStaticSourceDirectory("src/desktopTest/resources")
        }
    }
}
