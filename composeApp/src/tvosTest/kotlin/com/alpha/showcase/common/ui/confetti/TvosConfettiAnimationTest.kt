package com.alpha.showcase.common.ui.confetti

import io.github.vinceglb.confettikit.core.PartySystem
import io.github.vinceglb.confettikit.core.emitter.PartyEmitter
import io.github.vinceglb.confettikit.core.models.CoreRect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvosConfettiAnimationTest {
    private val drawArea = CoreRect.CoreRectImpl(width = 1920f, height = 1080f)

    @Test
    fun celebrationMatchesIosEmissionBudget() {
        val party = confettiParties(ConfettiType.Celebration).single()
        val emitter = PartyEmitter(party.emitter, pixelDensity = 1f, random = Random(42))
        var emitted = 0
        repeat(500) { emitted += emitter.createConfetti(0.01f, party, drawArea).size }

        assertEquals(500, emitted, "iOS emits 100 particles per second for five seconds")
        assertTrue(emitter.isFinished())
        assertTrue(emitter.createConfetti(60f, party, drawArea).isEmpty())
    }

    @Test
    fun everyPresetStopsEmittingAndReleasesAllParticles() {
        ConfettiType.entries.forEach { type ->
            // Exercise 120 Hz, 60 Hz, slower frames, and a frame crossing the emission deadline.
            listOf(0.008f, 0.016f, 0.033f, 0.1f).forEach { deltaSeconds ->
                val system = PartySystem(confettiParties(type).single(), pixelDensity = 1f, random = Random(7))
                var sawParticles = false
                repeat((9f / deltaSeconds).toInt()) {
                    val particles = system.render(deltaSeconds, drawArea)
                    sawParticles = sawParticles || particles.isNotEmpty()
                    assertTrue(system.getActiveParticleAmount() <= 500)
                    particles.forEach { particle ->
                        assertTrue(particle.x.isFinite() && particle.y.isFinite())
                        assertTrue(particle.scaleX in 0f..1f)
                        assertTrue(particle.alpha in 0..255)
                    }
                }
                assertTrue(sawParticles, "$type must be visible")
                assertTrue(system.isDoneEmitting(), "$type must finish at $deltaSeconds seconds per frame")
                assertEquals(0, system.getActiveParticleAmount())
                assertTrue(system.render(60f, drawArea).isEmpty())
            }
        }
    }

    @Test
    fun particlesUseIosLifetimeAndFadeInsteadOfDisappearingAbruptly() {
        val party = confettiParties(ConfettiType.Burst).single()
        val emitter = PartyEmitter(party.emitter, pixelDensity = 1f, random = Random(42))
        val particle = emitter.createConfetti(party.emitter.amountPerMs, party, drawArea).single()

        repeat(125) { particle.render(0.016f, drawArea) }
        assertEquals(255, particle.alpha)
        repeat(25) { particle.render(0.016f, drawArea) }
        assertTrue(particle.alpha in 1..254)
        assertFalse(particle.isDead())
        repeat(30) { particle.render(0.016f, drawArea) }
        assertTrue(particle.isDead())
    }

    @Test
    fun densityScalesParticleSizeAndMovementTogether() {
        val party = confettiParties(ConfettiType.Burst).single()
        val normal = PartySystem(party, pixelDensity = 1f, random = Random(42))
        val retina = PartySystem(party, pixelDensity = 2f, random = Random(42))
        val retinaArea = drawArea.copy(width = drawArea.width * 2, height = drawArea.height * 2)
        repeat(60) {
            val first = normal.render(0.016f, drawArea)
            val second = retina.render(0.016f, retinaArea)
            assertEquals(first.size, second.size)
            first.zip(second).forEach { (a, b) ->
                assertEquals(a.x * 2, b.x, 0.001f)
                assertEquals(a.y * 2, b.y, 0.001f)
                assertEquals(a.width * 2, b.width)
                assertEquals(a.color, b.color)
                assertEquals(a.rotation, b.rotation)
            }
        }
    }
}
