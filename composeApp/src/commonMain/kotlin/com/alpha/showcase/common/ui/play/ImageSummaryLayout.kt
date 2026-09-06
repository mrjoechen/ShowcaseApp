package com.alpha.showcase.common.ui.play

import kotlin.math.max
import kotlin.math.min

internal data class VisibleImageBounds(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float
    get() = (right - left).coerceAtLeast(0f)

  val height: Float
    get() = (bottom - top).coerceAtLeast(0f)
}

internal data class HorizontalRevealMask(
  val startX: Float,
  val endX: Float,
)

/**
 * Positions an opaque-to-transparent feather so it starts completely left of the content and
 * finishes completely right of it. Used as a DstIn mask for a one-shot left-to-right reveal.
 */
internal fun calculateHorizontalRevealMask(
  widthPx: Float,
  progress: Float,
  featherPx: Float,
): HorizontalRevealMask {
  val safeWidth = widthPx.nonNegativeFiniteOrZero()
  val safeFeather = featherPx.nonNegativeFiniteOrZero()
  val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
  val edgeX = (safeWidth + safeFeather) * safeProgress
  return HorizontalRevealMask(
    startX = edgeX - safeFeather,
    endX = edgeX,
  )
}

/**
 * Returns the part of a centered image that is visible inside its container.
 *
 * A fitted image may leave letterboxing around its rendered bounds. Centered zoom reduces that
 * letterboxing, and any part extending outside the container is clipped. A cropped image fills
 * the complete viewport. Invalid or shrinking zoom falls back to 1. Missing source dimensions
 * fall back to the viewport to keep overlays usable while loading.
 */
internal fun calculateVisibleImageBounds(
  containerWidth: Float,
  containerHeight: Float,
  imageWidth: Float,
  imageHeight: Float,
  contentScaleFit: Boolean,
  imageScale: Float = 1f,
): VisibleImageBounds {
  if (!containerWidth.isFinite() || !containerHeight.isFinite() ||
    containerWidth <= 0f || containerHeight <= 0f
  ) {
    return VisibleImageBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
  }

  val viewport = VisibleImageBounds(
    left = 0f,
    top = 0f,
    right = containerWidth,
    bottom = containerHeight,
  )
  if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) {
    return viewport
  }
  if (!contentScaleFit) return viewport

  val zoom = imageScale.takeIf { it.isFinite() && it >= 1f } ?: 1f
  val fitScale = min(containerWidth / imageWidth, containerHeight / imageHeight)
  val visibleWidth = min(containerWidth, imageWidth * fitScale * zoom)
  val visibleHeight = min(containerHeight, imageHeight * fitScale * zoom)
  val left = (containerWidth - visibleWidth) / 2f
  val top = (containerHeight - visibleHeight) / 2f
  return VisibleImageBounds(
    left = left,
    top = top,
    right = left + visibleWidth,
    bottom = top + visibleHeight,
  )
}

/**
 * Converts an inset from the image's bottom edge into a screen-bottom inset, reserving only the
 * extra space still needed for music controls after any existing bottom letterbox is considered.
 */
internal fun calculateSummaryBottomInset(
  containerHeight: Float,
  imageBottom: Float,
  contentInset: Float,
  musicReservedInset: Float,
): Float {
  val safeContentInset = contentInset.nonNegativeFiniteOrZero()
  val safeMusicReservedInset = musicReservedInset.nonNegativeFiniteOrZero()
  val bottomLetterbox = calculateBottomLetterbox(containerHeight, imageBottom)
  return max(bottomLetterbox + safeContentInset, safeMusicReservedInset)
}

internal fun calculateSummaryLocalBottomInset(
  containerHeight: Float,
  imageBottom: Float,
  contentInset: Float,
  musicReservedInset: Float,
): Float {
  val bottomLetterbox = calculateBottomLetterbox(containerHeight, imageBottom)
  return calculateSummaryBottomInset(
    containerHeight = containerHeight,
    imageBottom = imageBottom,
    contentInset = contentInset,
    musicReservedInset = musicReservedInset,
  ) - bottomLetterbox
}

private fun calculateBottomLetterbox(containerHeight: Float, imageBottom: Float): Float {
  if (!containerHeight.isFinite() || !imageBottom.isFinite()) return 0f
  return (containerHeight - imageBottom).coerceAtLeast(0f)
}

private fun Float.nonNegativeFiniteOrZero(): Float =
  if (isFinite()) coerceAtLeast(0f) else 0f
