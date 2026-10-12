plugins { `kotlin-dsl` }

kotlin { jvmToolchain(17) }
dependencies {
    // Application plugins supply these at runtime; the source verifier does not apply them.
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly("io.sentry:sentry-kotlin-multiplatform-gradle-plugin:${libs.versions.sentryKotlinMultiplatform.get()}")
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("upstreamSources") {
            id = "showcase.upstream-sources"
            implementationClass = "showcase.buildlogic.UpstreamSourcesPlugin"
        }
        register("platforms") {
            id = "showcase.platforms"
            implementationClass = "showcase.buildlogic.ShowcasePlatformsPlugin"
        }
        register("composePlatforms") {
            id = "showcase.compose-platforms"
            implementationClass = "showcase.buildlogic.ComposePlatformsPlugin"
        }
    }
}
