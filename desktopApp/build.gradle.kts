@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.tasks.JavaExec
import java.io.File
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

private fun isJarSignatureEntry(entryName: String): Boolean {
    val normalizedName = entryName.uppercase(Locale.ROOT)
    if (!normalizedName.startsWith("META-INF/")) return false
    val fileName = normalizedName.substringAfterLast('/')
    return fileName.startsWith("SIG-") ||
        fileName.endsWith(".SF") ||
        fileName.endsWith(".RSA") ||
        fileName.endsWith(".DSA") ||
        fileName.endsWith(".EC")
}

private fun stripInvalidJarSignatures(jarFile: File): Int {
    val temporaryJar = File.createTempFile("${jarFile.name}.", ".unsigned", jarFile.parentFile)
    var removedEntries = 0
    try {
        ZipInputStream(jarFile.inputStream().buffered()).use { input ->
            ZipOutputStream(temporaryJar.outputStream().buffered()).use { output ->
                while (true) {
                    val inputEntry = input.nextEntry ?: break
                    if (isJarSignatureEntry(inputEntry.name)) {
                        removedEntries += 1
                        input.closeEntry()
                        continue
                    }

                    output.putNextEntry(ZipEntry(inputEntry.name).apply {
                        time = inputEntry.time
                    })
                    input.copyTo(output)
                    output.closeEntry()
                    input.closeEntry()
                }
            }
        }
        Files.move(
            temporaryJar.toPath(),
            jarFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        temporaryJar.delete()
    }
    return removedEntries
}

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
                optimize.set(false)
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

// ProGuard joins signed third-party jars into a rewritten output. Their original
// signature blocks no longer match and must not be shipped in the merged jar.
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    doLast {
        outputs.files.asFileTree
            .matching { include("**/*.jar") }
            .files
            .forEach { jarFile ->
                val removedEntries = stripInvalidJarSignatures(jarFile)
                if (removedEntries > 0) {
                    logger.lifecycle(
                        "Removed $removedEntries invalid signature entries from ${jarFile.name}",
                    )
                }
            }
    }
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
