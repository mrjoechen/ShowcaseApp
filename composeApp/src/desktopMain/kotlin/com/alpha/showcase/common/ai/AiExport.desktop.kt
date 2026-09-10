package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean {
    val file = FileKit.openFileSaver(suggestedName = name.substringBeforeLast('.'), defaultExtension = name.substringAfterLast('.'))
    if (file == null) return false
    file.write(bytes)
    return true
}

internal actual suspend fun shareAiImage(name: String, bytes: ByteArray) {
    val image = javax.imageio.ImageIO.read(bytes.inputStream()) ?: error("Cannot decode image for sharing")
    val content = object : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor == java.awt.datatransfer.DataFlavor.imageFlavor
        override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
            return image
        }
    }
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(content, null)
}
