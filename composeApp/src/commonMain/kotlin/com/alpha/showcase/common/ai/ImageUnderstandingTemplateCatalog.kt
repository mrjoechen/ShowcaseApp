package com.alpha.showcase.common.ai

/** Seam for built-in, remote, or cached image-understanding template adapters. */
fun interface ImageUnderstandingTemplateCatalog {
    fun templates(): List<ImageUnderstandingTemplate>

    fun template(id: String): ImageUnderstandingTemplate? = templates().firstOrNull { it.id == id }
}
