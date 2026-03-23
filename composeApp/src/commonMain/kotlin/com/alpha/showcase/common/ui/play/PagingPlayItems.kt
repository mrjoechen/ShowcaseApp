package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A Compose-friendly paged data source for play/showcase UI components.
 *
 * Items are loaded in pages on demand and cached in memory with a window-based
 * eviction strategy to prevent OOM when dealing with large datasets.
 *
 * UI components access items by index via [get]. If the page containing that index
 * is not loaded yet, a fallback item from an already-loaded page is returned and
 * an async load is triggered. Once the page arrives, the Compose snapshot state
 * map update triggers recomposition so the correct item is displayed.
 */
@Stable
class PagingPlayItems(
    val totalCount: Int,
    initialPage: List<Any>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val coroutineScope: CoroutineScope,
    private val loadPage: suspend (offset: Int, limit: Int) -> List<Any>,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 200
        private const val MAX_PAGES_IN_MEMORY = 5

        /**
         * Wrap a full in-memory list as a [PagingPlayItems] for API compatibility
         * with sources that don't use paging (e.g. GitHub, TMDB, Unsplash).
         */
        fun fromList(list: List<Any>, scope: CoroutineScope): PagingPlayItems {
            return PagingPlayItems(
                totalCount = list.size,
                initialPage = list,
                pageSize = list.size.coerceAtLeast(1),
                coroutineScope = scope,
                loadPage = { _, _ -> emptyList() }
            )
        }
    }

    private val pages = mutableStateMapOf<Int, List<Any>>()
    private val loadingPages = mutableSetOf<Int>()

    val size: Int get() = totalCount

    init {
        if (initialPage.isNotEmpty()) {
            pages[0] = initialPage
        }
    }

    /**
     * Get the item at the given index (modulo totalCount for cycling).
     * Always returns a non-null value: if the target page is not loaded yet,
     * returns a fallback from the nearest loaded page while loading proceeds.
     */
    operator fun get(index: Int): Any {
        if (totalCount == 0 || pages.isEmpty()) {
            error("PagingPlayItems is empty")
        }
        val safeIndex = index % totalCount
        val pageNum = safeIndex / pageSize
        val pageOffset = safeIndex % pageSize

        val page = pages[pageNum]
        if (page != null && pageOffset < page.size) {
            preloadAdjacent(pageNum)
            return page[pageOffset]
        }

        // Target page not loaded - request it and return fallback
        requestPage(pageNum)
        return getFallback(pageNum, pageOffset)
    }

    /**
     * Get a random item from currently loaded pages.
     */
    fun randomLoaded(): Any {
        val allPages = pages.values.toList()
        if (allPages.isEmpty()) error("No loaded pages")
        val page = allPages[kotlin.random.Random.nextInt(allPages.size)]
        return page[kotlin.random.Random.nextInt(page.size)]
    }

    /**
     * Get a batch of items starting from a given index.
     * Returns immediately available items from loaded pages.
     */
    fun getRange(startIndex: Int, count: Int): List<Any> {
        val result = mutableListOf<Any>()
        for (i in startIndex until (startIndex + count).coerceAtMost(totalCount)) {
            result.add(get(i))
        }
        return result
    }

    /**
     * Trigger preloading of the page containing the given index.
     */
    fun preload(index: Int) {
        if (totalCount == 0) return
        val pageNum = (index % totalCount) / pageSize
        requestPage(pageNum)
    }

    private fun requestPage(pageNum: Int) {
        if (pageNum in loadingPages || pages.containsKey(pageNum)) return
        val offset = pageNum * pageSize
        if (offset >= totalCount) return

        loadingPages.add(pageNum)
        coroutineScope.launch {
            try {
                val items = loadPage(offset, pageSize)
                if (items.isNotEmpty()) {
                    pages[pageNum] = items
                }
            } finally {
                loadingPages.remove(pageNum)
            }
            evictDistantPages(pageNum)
        }
    }

    private fun preloadAdjacent(currentPage: Int) {
        if ((currentPage + 1) * pageSize < totalCount) {
            requestPage(currentPage + 1)
        }
        if (currentPage > 0) {
            requestPage(currentPage - 1)
        }
    }

    private fun evictDistantPages(currentPage: Int) {
        if (pages.size <= MAX_PAGES_IN_MEMORY) return
        val toEvict = pages.keys
            .sortedByDescending { abs(it - currentPage) }
            .drop(MAX_PAGES_IN_MEMORY)
        toEvict.forEach { pages.remove(it) }
    }

    private fun getFallback(targetPage: Int, offset: Int): Any {
        val nearest = pages.keys.minByOrNull { abs(it - targetPage) }
            ?: return pages.values.first().first()
        val page = pages[nearest] ?: return pages.values.first().first()
        return page[offset.coerceIn(0, page.size - 1)]
    }
}
