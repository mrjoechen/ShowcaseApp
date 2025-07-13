import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.buildConfig)
}

kotlin {
	androidTarget {
		compilations.all {
			compileTaskProvider {
				compilerOptions {
					jvmTarget.set(JvmTarget.JVM_1_8)
					freeCompilerArgs.add("-Xjdk-release=${JavaVersion.VERSION_1_8}")
				}
			}
		}
	}

	jvm()

	@OptIn(ExperimentalWasmDsl::class)
	listOf(
		js(),
		wasmJs(),
	).forEach {
		it.outputModuleName = "showcase-api"
		it.browser()
		it.binaries.executable()

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

		named("wasmJsMain") {
			dependencies {

			}
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

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
}

buildConfig {
	useKotlinOutput { topLevelConstants = true }
	packageName("com.alpha.showcase.api")

	val localProperties = gradleLocalProperties(rootDir, providers)
	val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY")
	require(tmdbApiKey.isNotEmpty()) {
		"Register your api TMDB_API_KEY place it in local.properties as `TMDB_API_KEY`"
	}

	val pexelsApiKey: String = localProperties.getProperty("PEXELS_API_KEY")
	require(pexelsApiKey.isNotEmpty()) {
		"Register your api PEXELS_API_KEY place it in local.properties as `PEXELS_API_KEY`"
	}

	val unsplashApiKey: String = localProperties.getProperty("UNSPLASH_API_KEY")
	require(unsplashApiKey.isNotEmpty()) {
		"Register your api UNSPLASH_API_KEY place it in local.properties as `UNSPLASH_API_KEY`"
	}


	buildConfigField("PEXELS_API_KEY", pexelsApiKey)
	buildConfigField("UNSPLASH_API_KEY", unsplashApiKey)
	buildConfigField("TMDB_API_KEY", tmdbApiKey)
}
