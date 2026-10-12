import showcase.buildlogic.UpstreamSources

plugins {
    alias(libs.plugins.kotlinJvm)
    id("showcase.upstream-sources")
}

kotlin { jvmToolchain(17) }
val upstream = extensions.getByType<UpstreamSources>().apply {
    repositoryRoot.set(layout.projectDirectory.dir("../.."))
}
val confetti = upstream.confetti()
val reference = sourceSets.create("reference")

// These are the application's real enum and presets, compiled independently for each engine.
kotlin.sourceSets {
    listOf("main", "reference").forEach { name ->
        named(name) {
            kotlin.srcDirs(
                "../../composeApp/src/confettiMain/kotlin",
                "../../composeApp/src/commonMain/kotlin",
                "src/trace/kotlin",
            )
            kotlin.include("**/ConfettiType.kt", "**/ConfettiParties.kt", "**/Trace.kt")
        }
    }
    named("main") {
        kotlin.srcDir(confetti.flatMap { it.outputDirectory })
        kotlin.include("io/github/vinceglb/confettikit/core/**/*.kt")
    }
    named("test") {
        kotlin.srcDir("../../composeApp/src/tvosTest/kotlin")
        kotlin.include("**/TvosConfettiAnimationTest.kt")
    }
}
dependencies {
    implementation(libs.compose.ui)
    add(reference.implementationConfigurationName, libs.confettikit)
    testImplementation(kotlin("test-junit"))
}

fun registerTrace(name: String, sourceSet: SourceSet) = tasks.register<JavaExec>(name) {
    group = "verification"
    classpath = sourceSet.runtimeClasspath
    mainClass.set("com.alpha.showcase.common.ui.confetti.TraceKt")
    val report = layout.buildDirectory.file("reports/confetti/$name.txt")
    outputs.file(report)
    args(report.get().asFile.absolutePath, sourceSet.name)
}
val sourceTrace = registerTrace("sourceTrace", sourceSets.main.get())
val referenceTrace = registerTrace("referenceTrace", reference)

tasks.register<Exec>("verifyParity") {
    group = "verification"
    description = "Compare 36 seeded animation traces with the published ConfettiKit binary"
    dependsOn(sourceTrace, referenceTrace)
    inputs.files(sourceTrace.map { it.outputs.files }, referenceTrace.map { it.outputs.files })
    inputs.file("compare_traces.py")
    commandLine("python3", "compare_traces.py", "build/reports/confetti/sourceTrace.txt", "build/reports/confetti/referenceTrace.txt")
}
tasks.check { dependsOn("verifyParity") }
