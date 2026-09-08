rootProject.name = "ShowcaseApp"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven(rootDir.resolve("local-face-detection/opencv-repo")) {
            content { includeGroup("com.alpha.thirdparty") }
        }
        // Openize publishes its Java HEIC decoder in this vendor repository.
        exclusiveContent {
            forRepository { maven("https://releases.aspose.com/java/repo/") }
            filter { includeModule("openize", "openize-heic") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
    }
}

include(":composeApp")
include(":androidApp")
include(":desktopApp")
include(":showcase-api")
include(":sqliteWasmWorker")
include(":ai-model-capabilities")
include(":local-face-detection")

//include(":rclone")
