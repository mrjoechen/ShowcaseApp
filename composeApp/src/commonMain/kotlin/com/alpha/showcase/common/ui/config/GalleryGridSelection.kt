package com.alpha.showcase.common.ui.config

/** A drag always applies to its starting snapshot, so reversing restores the original selection. */
internal class GalleryGridSelection(
    private val mediaUris: List<String>,
    private val anchor: Int,
    private val initiallySelected: Set<String>,
) {
    private val selecting = mediaUris[anchor] !in initiallySelected

    fun selectionAt(index: Int): Set<String> {
        val range = mediaUris.subList(minOf(anchor, index), maxOf(anchor, index) + 1).toSet()
        return if (selecting) initiallySelected + range else initiallySelected - range
    }
}
