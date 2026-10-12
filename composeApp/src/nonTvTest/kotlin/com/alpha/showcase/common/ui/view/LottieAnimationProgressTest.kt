package com.alpha.showcase.common.ui.view

import io.github.alexzhirkevich.compottie.LottieAnimatable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LottieAnimationProgressTest {

    @Test
    fun progressProviderReadsTheCurrentAnimationFrame() = runTest {
        val animationState = LottieAnimatable()
        animationState.snapTo(progress = 0f)
        val progressProvider = lottieProgressProvider(animationState)

        animationState.snapTo(progress = 0.75f)

        assertEquals(0.75f, progressProvider())
    }
}
