@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.tasks.JavaExec
import java.io.File
import org.gradle.api.GradleException
import java.text.SimpleDateFormat
import java.util.Calendar

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}
apply(from = "../version.gradle.kts")

private val currentOsName = System.getProperty("os.name").orEmpty()
private val isWindowsHost = currentOsName.contains("windows", ignoreCase = true)
private val isMacHost = currentOsName.contains("mac", ignoreCase = true)
private val DESKTOP_REQUIRED_JAVA_MAJOR = 21
private val jpackageBinaryName = if (isWindowsHost) "jpackage.exe" else "jpackage"
private val javaBinaryName = if (isWindowsHost) "java.exe" else "java"

private fun normalizeJdkHome(javaHome: String): File? {
    if (javaHome.isBlank()) return null
    val home = File(javaHome).absoluteFile
    if (!home.exists()) return null
    if (home.resolve("release").exists()) return home
    // Some distributions expose java.home as <jdk>/jre
    val parent = home.parentFile ?: return null
    return parent.takeIf { it.resolve("release").exists() }
}

private fun hasJpackageTool(javaHome: File): Boolean =
    javaHome.resolve("bin/$jpackageBinaryName").exists()

private fun readJavaMajorVersion(javaHome: File): Int? {
    val releaseFile = javaHome.resolve("release")
    if (!releaseFile.isFile) return null
    val javaVersionLine = releaseFile.useLines { lines ->
        lines.firstOrNull { it.startsWith("JAVA_VERSION=") }
    } ?: return null
    val rawVersion = javaVersionLine.substringAfter('=').trim().trim('"')
    return rawVersion.substringBefore('.').toIntOrNull()
}

private fun resolveMacJavaHome(requiredJavaMajor: Int): String? {
    if (!isMacHost) return null
    return runCatching {
        val process = ProcessBuilder(
            "/usr/libexec/java_home",
            "-v",
            requiredJavaMajor.toString()
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        output.takeIf { exitCode == 0 && it.isNotBlank() }
    }.getOrNull()
}

private fun resolveJpackageJavaHome(project: Project, requiredJavaMajor: Int): String {
    val candidates = buildList {
        add((project.findProperty("desktop.javaHome") as? String).orEmpty())
        add(System.getenv("JDK_HOME").orEmpty())
        add(System.getenv("JAVA_HOME").orEmpty())
        add(System.getProperty("org.gradle.java.home").orEmpty())
        add(System.getProperty("java.home").orEmpty())
        add(resolveMacJavaHome(requiredJavaMajor).orEmpty())
    }.mapNotNull { normalizeJdkHome(it.trim()) }
        .distinctBy { it.absolutePath }

    val selected = candidates.firstOrNull { home ->
        hasJpackageTool(home) && readJavaMajorVersion(home) == requiredJavaMajor
    }
    return selected?.absolutePath ?: throw GradleException(
        "No JDK $requiredJavaMajor with jpackage found. Checked: " +
            candidates.joinToString { home ->
                val version = readJavaMajorVersion(home)?.toString() ?: "unknown"
                "${home.absolutePath} (java=$version, jpackage=${hasJpackageTool(home)})"
            } +
            ". Please install JDK $requiredJavaMajor and set -Pdesktop.javaHome=<JDK_HOME> " +
            "or JAVA_HOME."
    )
}

private val desktopJavaHome = resolveJpackageJavaHome(project, DESKTOP_REQUIRED_JAVA_MAJOR)
private val desktopJavaExecutable = File(desktopJavaHome, "bin/$javaBinaryName").absolutePath

kotlin {
    jvmToolchain(DESKTOP_REQUIRED_JAVA_MAJOR)
    jvm {
        mainRun {
            mainClass = "Showcase"
        }
    }
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.network)
                implementation(libs.kotlinx.datetime)
                implementation(libs.flatlaf)
                implementation(project(":composeApp"))
            }
        }
    }
}

compose.desktop {
    application {
        javaHome = desktopJavaHome
        project.version = project.extra["versionCode"].toString()
        mainClass = "Showcase"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Pkg, TargetFormat.Rpm)
            packageName = "Showcase"
            packageVersion = project.extra["versionName"] as String
            version = project.version
            description = "Showcase App"
            copyright = "© 2025 Joe Chen and ShowcaseApp Contributors."
            vendor = "GitHub"
            licenseFile.set(rootProject.file("LICENSE"))
            // 设置 resources 拷贝到本地
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            buildTypes.release.proguard {
                configurationFiles.from("compose-desktop.pro")
                obfuscate.set(true)
                joinOutputJars.set(true)
            }
            val iconsRoot = project.file("resources")
            macOS {
                // macOS specific options
                iconFile.set(iconsRoot.resolve("Showcase.icns"))
                bundleID = "com.alpha.showcase.macos"
                dockName = "Showcase App"
                dmgPackageVersion = project.version.toString()
                pkgPackageVersion = project.version.toString()
            }
            windows {
                // Windows specific options
                iconFile.set(iconsRoot.resolve("Showcase.ico"))
                menuGroup = "Showcase App"
                // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                upgradeUuid = "18159995-d967-4CD2-8885-77BFA97CFA9F"
            }
            linux {
                // Linux specific options
                iconFile.set(iconsRoot.resolve("Showcase.png"))
                modules("jdk.security.auth")
            }
        }
    }
}

val desktopCrashDir = layout.buildDirectory.dir("desktop-crash")
tasks.withType<JavaExec>().configureEach {
    if (!(name.contains("jvmRun", ignoreCase = true) || name.equals("run", ignoreCase = true))) {
        return@configureEach
    }
    executable = desktopJavaExecutable
    doFirst {
        desktopCrashDir.get().asFile.mkdirs()
    }
    jvmArgs(
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=${desktopCrashDir.get().asFile.absolutePath}/heapdump.hprof",
        "-XX:ErrorFile=${desktopCrashDir.get().asFile.absolutePath}/hs_err_pid%p.log",
        "-Dskiko.renderApi=SOFTWARE"
    )
}


afterEvaluate {
    tasks.findByName("packageDistributionForCurrentOS")?.finalizedBy("renameDistributionFiles")
    tasks.findByName("packageReleaseDistributionForCurrentOS")?.finalizedBy("renameDistributionFiles")
}

tasks.register("renameDistributionFiles") {
    doLast {
        // 获取构建产物目录
        val prefixName = SimpleDateFormat("yyyyMMddHHmm").format(Calendar.getInstance().time) + "-${project.extra["versionHash"]}"

        val outputDirs = listOf(
            layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile to "macos",
            layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile to "windows",
            layout.buildDirectory.dir("compose/binaries/main/exe").get().asFile to "windows",
            layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile to "linux",
            layout.buildDirectory.dir("compose/binaries/main/pkg").get().asFile to "macos",
            layout.buildDirectory.dir("compose/binaries/main/rpm").get().asFile to "linux",
            layout.buildDirectory.dir("compose/binaries/main-release/dmg").get().asFile to "macos",
            layout.buildDirectory.dir("compose/binaries/main-release/msi").get().asFile to "windows",
            layout.buildDirectory.dir("compose/binaries/main-release/exe").get().asFile to "windows",
            layout.buildDirectory.dir("compose/binaries/main-release/deb").get().asFile to "linux",
            layout.buildDirectory.dir("compose/binaries/main-release/pkg").get().asFile to "macos",
            layout.buildDirectory.dir("compose/binaries/main-release/rpm").get().asFile to "linux"
        )

        outputDirs.forEach { (outputDir, platformName) ->
            outputDir.listFiles()?.forEach {
                println(it.absolutePath)
                val originalFile = outputDir.resolve(it.name)
                val baseName = originalFile.nameWithoutExtension
                val platformTaggedName = if (baseName.contains(platformName, ignoreCase = true)) {
                    baseName
                } else {
                    "$baseName-$platformName"
                }
                val targetFile = outputDir.resolve("$platformTaggedName-$prefixName.${originalFile.extension}")
                if (originalFile.exists()) {
                    originalFile.renameTo(targetFile)
                    logger.lifecycle("✅ ${originalFile.name} → ${targetFile.name}")
                    println(targetFile.absolutePath)
                } else {
                    logger.warn("❌ File Not Found: ${originalFile.absolutePath}")
                }
            }
        }
    }
}
