import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Properties
import kotlin.toString

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
val keystorePropertiesFile = rootProject.file("androidApp/keystore.properties")

android {
    namespace = "com.alpha.showcase.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    if (keystorePropertiesFile.exists()) {
      val keystoreProperties = Properties()
      keystoreProperties.load(FileInputStream(keystorePropertiesFile))
      signingConfigs {
        create("config") {
          keyAlias = keystoreProperties["keyAlias"].toString()
          keyPassword = keystoreProperties["keyPassword"].toString()
          storeFile = file(keystoreProperties["storeFile"]!!)
          storePassword = keystoreProperties["storePassword"].toString()
          println("Android KeyStoreFile: ${storeFile?.absolutePath} exists: ${storeFile?.exists()}")
        }
      }
    }

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
            applicationIdSuffix = ".android.dev"
            resValue("string", "app_name", "ShowcaseApp Dev")
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("config")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("config")
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
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
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

    buildFeatures {
        compose = true
    }

    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}
