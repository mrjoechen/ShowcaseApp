import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.flatlaf)
                implementation(project(":composeApp"))
            }
        }
    }
}

compose.desktop {
    application {
        project.version = project.extra["versionCode"].toString()
        mainClass = "Showcase"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Pkg, TargetFormat.Rpm)
            packageName = "Showcase"
            packageVersion = project.extra["versionName"] as String
            version = project.version
            description = "Showcase App"
            copyright = "© 2024 Joe Chen. All rights reserved."
            vendor = "GitHub"
            licenseFile.set(project.file("LICENSE.txt"))
            // 设置 resources 拷贝到本地
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            buildTypes.release.proguard {
                configurationFiles.from("rules.pro")
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


tasks.register("renameDesktopArtifact") {
    // 确保该任务在桌面构建任务完成后执行
    dependsOn("packageDistributionForCurrentOS")
    doLast {
        // 获取构建产物目录
        val prefixName = SimpleDateFormat("yyyyMMddHHmm").format(Calendar.getInstance().time) + "-${project.extra["versionHash"]}"

        val outputDirs = listOf(
            layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile,
            layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile,
            layout.buildDirectory.dir("compose/binaries/main/exe").get().asFile,
            layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile,
            layout.buildDirectory.dir("compose/binaries/main/pkg").get().asFile,
            layout.buildDirectory.dir("compose/binaries/main/rpm").get().asFile
        )
        outputDirs.forEach { outputDir ->
            // 根据你的实际文件名定义原始文件和目标文件
            outputDir.listFiles()?.forEach {
                println(it.absolutePath)
                val originalFile = outputDir.resolve(it.name)
                val targetFile = outputDir.resolve("${originalFile.nameWithoutExtension}-$prefixName.${originalFile.extension}")
                if (originalFile.exists()) {
                    originalFile.renameTo(targetFile)
                    println("✅ ${originalFile.name} → ${targetFile.name}")
                } else {
                    println("❌ File Not Found: ${originalFile.absolutePath}")
                }
            }
        }
    }
}


