package com.alpha.showcase.common.ui.confetti

import io.github.vinceglb.confettikit.core.PartySystem
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

// Internal Kotlin method names contain a module suffix. Reflection lets the same
// harness call the published JVM binary without suppressing Kotlin visibility checks.
private fun engineMethod(name: String, parameterCount: Int) = PartySystem::class.java.methods.single {
    it.name.substringBefore('$') == name && it.parameterCount == parameterCount
}

private object TraceOrigin

fun main(args: Array<String>) {
    val engine = PartySystem::class.java
    val location = engine.protectionDomain.codeSource.location
    val localClasses = TraceOrigin::class.java.protectionDomain.codeSource.location
    check(engine.classLoader.getResources("io/github/vinceglb/confettikit/core/PartySystem.class").toList().size == 1) {
        "Multiple ConfettiKit engines on the classpath would invalidate the comparison"
    }
    when (args[1]) {
        "main" -> check(location == localClasses) { "Source trace must execute the extracted engine" }
        "reference" -> check(location != localClasses && File(location.toURI()).name.startsWith("confettikit-jvm")) {
            "Reference trace must execute the published JVM binary"
        }
        else -> error("Unknown trace variant: ${args[1]}")
    }
    println("${args[1]} engine: $location")
    val render = engineMethod("render", 2)
    val active = engineMethod("getActiveParticleAmount", 0)
    val finished = engineMethod("isDoneEmitting", 0)
    val areaConstructor = Class.forName("io.github.vinceglb.confettikit.core.models.CoreRect\$CoreRectImpl")
        .getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
    val output = File(args[0]).apply { parentFile.mkdirs() }
    output.bufferedWriter().use { report ->
        ConfettiType.entries.forEach { type ->
            listOf(1f, 2f, 3f).forEach { density ->
                listOf(8L, 16L, 17L, 33L).forEach { frameMs ->
                    val system = PartySystem(confettiParties(type).single(), createdAt = 0L, pixelDensity = density, random = Random(42))
                    val area = areaConstructor.newInstance(0f, 0f, 1920f * density, 1080f * density)
                    val digest = MessageDigest.getInstance("SHA-256")
                    repeat((9000 / frameMs).toInt()) { frame ->
                        var remaining = frameMs
                        while (remaining > 0) {
                            val step = minOf(16L, remaining)
                            remaining -= step
                            val particles = render.invoke(system, step / 1000f, area)
                            val trace = "$frame/$remaining:${active.invoke(system)}:${finished.invoke(system)}:$particles\n"
                            digest.update(trace.toByteArray(Charsets.UTF_8))
                        }
                    }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    report.appendLine("$type/$density/$frameMs=$hash")
                }
            }
        }
    }
}
