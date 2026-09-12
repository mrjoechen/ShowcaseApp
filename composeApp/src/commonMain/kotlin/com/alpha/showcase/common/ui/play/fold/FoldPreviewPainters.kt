package com.alpha.showcase.common.ui.play.fold

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Procedural stand-ins for IDE previews and offline rendering tests; no bundled photos. */
internal fun foldPreviewPainters(): List<Painter> = List(FoldDemoPhotoUrls.size) { index ->
    object : Painter() {
        override val intrinsicSize = Size(800f, 600f)

        override fun DrawScope.onDraw() {
            val colors = listOf(Color(0xFF46676C), Color(0xFFE2CDA0), Color(0xFF243B37))
            val cell = size.width / 16f
            for (row in 0..(size.height / cell).toInt()) {
                for (column in 0 until 16) {
                    drawRect(colors[(row + column + index) % colors.size],
                        topLeft = Offset(column * cell, row * cell), size = Size(cell, cell))
                }
            }
        }
    }
}
