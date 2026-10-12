// Loaded only by settings.gradle.kts when -Pshowcase.tvos=true is present.
buildscript {
    val versions = java.util.Properties().apply {
        settingsDir.resolve("gradle/tvos.versions.properties").inputStream().use { load(it) }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("dev.sajidali:compose-tvos:${versions.getProperty("redirectPlugin") ?: error("Missing redirectPlugin version")}")
    }
}

apply<dev.sajidali.compose.tvos.ComposeTvosRedirectSettingsPlugin>()

extensions.configure<dev.sajidali.compose.tvos.ComposeTvosRedirectSettingsExtension> {
    val versions = java.util.Properties().apply {
        settingsDir.resolve("gradle/tvos.versions.properties").inputStream().use { load(it) }
    }
    manifestUrl.set("")
    composeGradlePluginVersion.set(versions.getProperty("composePlugin") ?: error("Missing composePlugin version"))
}
