import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.kotlinx.serialization)
}

kotlin {
	androidTarget {
		compilations.all {
			kotlinOptions {
				jvmTarget = "${JavaVersion.VERSION_1_8}"
				freeCompilerArgs += "-Xjdk-release=${JavaVersion.VERSION_1_8}"
			}
		}
	}

	jvm()

	@OptIn(ExperimentalWasmDsl::class)
	wasmJs {
		browser()
		binaries.executable()
	}

	listOf(
		iosX64(),
		iosArm64(),
		iosSimulatorArm64()
	).forEach {
		it.binaries.framework {
			baseName = "showcase-api"
			isStatic = true
		}
	}

	sourceSets {
		commonMain.dependencies {
			implementation(libs.napier)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.client.logging)
			implementation(libs.ktor.client.auth)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.client.serialization.kotlinx.json)
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
		}

		androidMain.dependencies {
			implementation(libs.kotlinx.coroutines.android)
			implementation(libs.ktor.client.okhttp)
		}

		jvmMain.dependencies {
			implementation(libs.kotlinx.coroutines.swing)
			implementation(libs.ktor.client.okhttp)
		}

		iosMain.dependencies {
			implementation(libs.ktor.client.darwin)
		}
	}
}

android {
	namespace = "com.alpha.showcase.api"
	compileSdk = 34

	defaultConfig {
		minSdk = 23
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}
	sourceSets["main"].apply {
		manifest.srcFile("src/androidMain/AndroidManifest.xml")
		res.srcDirs("src/androidMain/res")
	}
}
