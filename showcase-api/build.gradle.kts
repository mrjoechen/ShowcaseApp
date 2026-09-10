import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidMultiplatformLibrary)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.buildConfig)
}

kotlin {
	applyDefaultHierarchyTemplate()

	android {
        namespace = "com.alpha.showcase.api"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 0 } }
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
        withHostTest {}
    }

	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_1_8)
		}
	}

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
		val nonWebMain by creating {
			dependsOn(commonMain.get())
		}
		val nonWebTest by creating {
			dependsOn(commonTest.get())
		}

		named("androidMain") { dependsOn(nonWebMain) }
		named("jvmMain") { dependsOn(nonWebMain) }
		named("iosMain") { dependsOn(nonWebMain) }
		named("androidHostTest") { dependsOn(nonWebTest) }
		named("jvmTest") { dependsOn(nonWebTest) }
		named("nativeTest") { dependsOn(nonWebTest) }

		commonMain.dependencies {
			implementation(libs.napier)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.cryptography.core)
			implementation(libs.cryptography.provider.optimal)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.client.logging)
			implementation(libs.ktor.client.auth)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.client.serialization.kotlinx.json)
			implementation(libs.ksoup)
			implementation(libs.ksoup.kotlinx)
			implementation(libs.ktor.client.ksoup)

		}

		commonTest.dependencies {
            implementation(libs.ktor.client.mock)
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

buildConfig {
	useKotlinOutput { topLevelConstants = true }
	packageName("com.alpha.showcase.api")

	val localProperties = gradleLocalProperties(rootDir, providers)
	fun requiredExternalImageApiKey(name: String) = providers.provider {
		localProperties.getProperty(name)?.takeIf(String::isNotEmpty)
			?: error("Register your api $name place it in local.properties as `$name`")
	}

	sourceSets.named("nonWebMain") {
		useKotlinOutput { topLevelConstants = true }
		packageName("com.alpha.showcase.api")
		buildConfigField("PEXELS_API_KEY", requiredExternalImageApiKey("PEXELS_API_KEY"))
		buildConfigField("UNSPLASH_API_KEY", requiredExternalImageApiKey("UNSPLASH_API_KEY"))
		buildConfigField("TMDB_API_KEY", requiredExternalImageApiKey("TMDB_API_KEY"))
	}
}
