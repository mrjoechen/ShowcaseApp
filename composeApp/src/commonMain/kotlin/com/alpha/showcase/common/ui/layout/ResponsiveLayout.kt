package com.alpha.showcase.common.ui.layout

internal const val COMPACT_WEB_MAX_WIDTH_DP = 600f

internal fun isCompactWebLayout(
    isWeb: Boolean,
    viewportWidthDp: Float,
): Boolean = isWeb && viewportWidthDp < COMPACT_WEB_MAX_WIDTH_DP

internal fun homeHorizontalPaddingDp(
    isWeb: Boolean,
    isDesktop: Boolean,
    viewportWidthDp: Float,
): Float {
    return when {
        isCompactWebLayout(isWeb, viewportWidthDp) -> 8f
        isWeb || isDesktop -> 20f
        else -> 0f
    }
}

internal data class SourceGridLayoutPolicy(
    val isVertical: Boolean,
    val fixedColumnCount: Int?,
    val minimumCellWidthDp: Float,
    val contentHorizontalPaddingDp: Float,
    val itemPaddingDp: Float,
    val cardAspectRatio: Float?,
    val fixedCardHeightDp: Float,
)

internal fun sourceGridLayoutPolicy(
    isWeb: Boolean,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
): SourceGridLayoutPolicy {
    val isVertical = viewportHeightDp > viewportWidthDp * 1.5f
    val compactWeb = isCompactWebLayout(isWeb, viewportWidthDp)
    // Use three columns on regular phones to keep cards compact. Narrower
    // embedded webviews retain two columns, then fall back to one column only
    // when two cards would become impractically narrow.
    val compactColumnCount = when {
        viewportWidthDp >= 360f -> 3
        viewportWidthDp >= 240f -> 2
        else -> 1
    }
    val minimumCellWidthDp = when {
        compactWeb -> 128f
        isVertical -> 100f
        else -> 140f
    }

    return SourceGridLayoutPolicy(
        isVertical = isVertical,
        fixedColumnCount = compactColumnCount.takeIf { compactWeb },
        minimumCellWidthDp = minimumCellWidthDp,
        contentHorizontalPaddingDp = if (compactWeb) 8f else 16f,
        itemPaddingDp = if (compactWeb) 4f else 8f,
        // The old fixed 150dp height was based on the 100dp minimum cell,
        // but adaptive columns become wider on phones and made the card look square.
        // Derive a portrait card height from the actual cell width on compact web.
        cardAspectRatio = if (compactWeb) 3f / 4f else null,
        fixedCardHeightDp = minimumCellWidthDp * 1.5f,
    )
}
