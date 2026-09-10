pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "IosMediaRepro"
include(":local-face-detection")
project(":local-face-detection").projectDir = file("../../local-face-detection")
