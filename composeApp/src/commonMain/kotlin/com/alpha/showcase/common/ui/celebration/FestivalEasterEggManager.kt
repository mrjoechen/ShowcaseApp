@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.celebration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ActiveFestivalAnimation(
    val animation: FestivalAnimation,
    val position: AnimationPosition,
)

object FestivalEasterEggManager {

    private val _currentAnimation = MutableStateFlow<ActiveFestivalAnimation?>(null)
    val currentAnimation: StateFlow<ActiveFestivalAnimation?> = _currentAnimation

    private var activeConfig: FestivalConfig? = null
    private var playCount = 0
    private var lastPlayTimeMs = 0L

    fun tryTrigger(): Boolean {
        activeConfig = Festivals.findActive()
        val config = activeConfig ?: return false
        if (config.maxPlaysPerSession in 1..playCount) return false
        if (_currentAnimation.value != null) return false

        val now = Clock.System.now().toEpochMilliseconds()
        val minIntervalMs = max(config.minIntervalMinutes, 0) * 60_000L
        if (now - lastPlayTimeMs < minIntervalMs) return false
        if (config.animations.isEmpty()) return false

        val animation = pickWeightedRandom(config.animations)
        val position = animation.positions.ifEmpty {
            listOf(AnimationPosition.FULL_SCREEN)
        }.random()

        _currentAnimation.value = ActiveFestivalAnimation(animation, position)
        lastPlayTimeMs = now
        playCount++
        return true
    }

    fun onAnimationFinished() {
        _currentAnimation.value = null
    }

    fun resetSession() {
        _currentAnimation.value = null
        activeConfig = null
        playCount = 0
        lastPlayTimeMs = 0L
    }

    fun getRandomIntervalMs(): Long {
        val config = activeConfig ?: return 60_000L
        val min = max(config.minIntervalMinutes, 0) * 60_000L
        val max = max(config.maxIntervalMinutes, config.minIntervalMinutes) * 60_000L
        return if (max <= min) min else Random.nextLong(min, max + 1)
    }

    private fun pickWeightedRandom(animations: List<FestivalAnimation>): FestivalAnimation {
        val totalWeight = animations.sumOf { max(it.weight, 1) }
        var random = Random.nextInt(totalWeight)
        for (animation in animations) {
            random -= max(animation.weight, 1)
            if (random < 0) return animation
        }
        return animations.last()
    }
}
