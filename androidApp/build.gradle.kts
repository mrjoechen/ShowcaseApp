import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Properties
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.FilterConfiguration
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}

// Transform the public APK artifact so AGP keeps the original outputs/apk/<variant>
// location and updates IDE/install metadata to reference the renamed files.
@DisableCachingByDefault(because = "Renames already-built APKs without changing their contents")
abstract class RenameApks @Inject constructor(private val fileSystem: FileSystemOperations) : DefaultTask() {
    private var profileNames: Map<String, String> = emptyMap()
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApks>>

    @get:Input
    abstract val buildTimestamp: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun rename() {
        check(apkDirectory.get().asFile.canonicalFile != destination.get().asFile.canonicalFile) {
            "APK transformation input and output must be separate directories"
        }
        val names = linkedMapOf<File, String>()
        transformationRequest.get().submit(this) { artifact ->
            val abi = artifact.filters.firstOrNull {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val name = "showcase-android.${artifact.versionName}_${artifact.versionCode}-${buildTimestamp.get()}-$abi-${variantName.get()}.apk"
            check(name !in names.values) { "APK outputs must have unique names: $name" }
            names[File(artifact.outputFile)] = name
            destination.file(name).get().asFile
        }
        check(names.isNotEmpty()) { "Missing APK outputs" }
        profileNames = names.map { (apk, name) ->
            "${apk.nameWithoutExtension}.dm" to "${name.removeSuffix(".apk")}.dm"
        }.toMap()
        fileSystem.sync {
            into(destination)
            // Preserve any sidecar files; AGP writes the transformed output-metadata.json.
            from(apkDirectory) {
                exclude("*.apk", "output-metadata.json")
                rename { profileNames[it] ?: it }
            }
            names.forEach { (apk, name) -> from(apk) { rename { name } } }
        }
    }

    // Runs after AGP saves the transformed metadata. AGP 9.1 preserves the input
    // baseline-profile paths, so relocate those references alongside the APKs.
    fun relocateBaselineProfiles() {
        val output = destination.get().asFile
        val metadataFile = output.resolve("output-metadata.json")
        val metadata = JsonParser.parseString(metadataFile.readText()).asJsonObject
        val profiles = metadata.getAsJsonArray("baselineProfiles") ?: return
        val input = apkDirectory.get().asFile.canonicalFile.toPath()
        profiles.forEach { group ->
            val files = group.asJsonObject.getAsJsonArray("baselineProfiles")
            files.forEachIndexed { index, entry ->
                val original = output.resolve(entry.asString).canonicalFile.toPath()
                check(original.startsWith(input)) { "Baseline profile is outside the input APK directory: $original" }
                val relative = input.relativize(original)
                val name = checkNotNull(profileNames[original.fileName.toString()]) {
                    "Baseline profile has no matching APK: $original"
                }
                val relocated = relative.resolveSibling(name)
                check(output.resolve(relocated.toString()).isFile) { "Missing baseline profile: $relocated" }
                files.set(index, com.google.gson.JsonPrimitive(relocated.toString().replace('\\', '/')))
            }
        }
        metadataFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(metadata))
    }
}

// See b/430991549: AGP 9.1 Lint can crash on string-valued external KTS references.
apply(from = file("../version.gradle.kts"))

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":composeApp"))
    debugImplementation(libs.android.compose.ui.tooling)
}

val Project.gitHash: String
    get() = project.extra["gitHash"] as String
val date = SimpleDateFormat("yyyyMMddHHmm")
val formattedDate: String = date.format(Calendar.getInstance().time)
val keystorePropertiesFile = rootProject.file("androidApp/keystore.properties")

android {
    namespace = "com.alpha.showcase.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // API 37 is published as android-37.0; explicitly select the minor SDK level.
    compileSdkMinor = 0
    sourceSets["main"].kotlin.directories.add("src/androidMain/kotlin")
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.directories.add("src/androidMain/res")
    sourceSets["main"].resources.directories.add("src/commonMain/resources")

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
        base.archivesName.set(
            "showcase-android-$versionCode.${gitHash}($versionName)${formattedDate}"
        )
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
            jniLibs.directories.add("lib")
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

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar(Char::uppercaseChar)
        val rename = tasks.register<RenameApks>("rename${suffix}Apks") {
            buildTimestamp.set(formattedDate)
            variantName.set(variant.name)
        }
        val request = variant.artifacts.use(rename)
            .wiredWithDirectories(RenameApks::apkDirectory, RenameApks::destination)
            .toTransformMany(SingleArtifact.APK)
        rename.configure {
            transformationRequest.set(request)
            // Register after toTransformMany, whose doLast writes AGP metadata.
            doLast { relocateBaselineProfiles() }
        }
    }
}
