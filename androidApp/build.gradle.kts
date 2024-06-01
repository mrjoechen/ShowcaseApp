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
apply(from = "../version.gradle.kts")

android.buildFeatures.buildConfig=true
kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":composeApp"))
            }
        }
    }
}



val Project.gitHash: String
    get() = project.extra["gitHash"] as String
val date = SimpleDateFormat("yyyyMMddHHmm")
val formattedDate: String = date.format(Calendar.getInstance().time)

android {
    namespace = "com.alpha.showcase.android"
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
                "showcase-android.${versionName}_${versionCode}-${formattedDate}-${name}.apk"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Showcase Dev")
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            resValue("string", "app_name", "Showcase")
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
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

    kotlin {
        jvmToolchain(17)
    }

    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}
