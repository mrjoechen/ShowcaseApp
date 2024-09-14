package com.alpha.showcase.common.ui.view

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable


fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = {
        slideInHorizontally(
            enterTween,
            initialOffsetX = { (it * initialOffset).toInt() }) + fadeIn(fadeSpec)
    },
    exitTransition = {
        slideOutHorizontally(
            exitTween,
            targetOffsetX = { -(it * initialOffset).toInt() }) + fadeOut(fadeSpec)
    },
    popEnterTransition = {
        slideInHorizontally(
            enterTween,
            initialOffsetX = { -(it * initialOffset).toInt() }) + fadeIn(fadeSpec)
    },
    popExitTransition = {
        slideOutHorizontally(
            exitTween,
            targetOffsetX = { (it * initialOffset).toInt() }) + fadeOut(fadeSpec)
    },
    content = content
)

const val DURATION_ENTER = 400
const val DURATION_EXIT = 200
const val initialOffset = 0.10f
val fadeTween = tween<Float>(durationMillis = DURATION_EXIT)
val fadeSpec = fadeTween

val enterTween =
    tween<IntOffset>(durationMillis = DURATION_ENTER)
val exitTween =
    tween<IntOffset>(durationMillis = DURATION_EXIT)