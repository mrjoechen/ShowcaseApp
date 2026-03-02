@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.alpha.showcase.common.security

import com.alpha.showcase.common.storage.fileManager
import com.alpha.showcase.common.storage.storageDir
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual object ConfigKeyProvider {
    private const val SECURE_DIR = ".secure"
    private const val KEY_FILE_NAME = "config_key_v2.bin"

    actual fun getOrCreateKeyMaterial(): ByteArray {
        val keyFilePath = resolveKeyFilePath()
        val existing = readKeyFile(keyFilePath)
        if (existing != null && existing.size == CONFIG_KEY_SIZE_BYTES) {
            return existing
        }

        val generated = CryptographyRandom.Default.nextBytes(CONFIG_KEY_SIZE_BYTES)
        persistKey(generated, keyFilePath)
        return generated
    }

    private fun resolveKeyFilePath(): String {
        val secureDir = "$storageDir/$SECURE_DIR"
        return "$secureDir/$KEY_FILE_NAME"
    }

    private fun readKeyFile(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val size = data.length.toInt()
        if (size <= 0) return null

        return ByteArray(size).also { output ->
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    }

    private fun persistKey(keyMaterial: ByteArray, path: String) {
        val secureDir = path.substringBeforeLast('/')
        val protectionAttributes: Map<Any?, Any?> = mapOf(
            NSFileProtectionKey as Any to NSFileProtectionCompleteUntilFirstUserAuthentication as Any
        )

        if (!fileManager.fileExistsAtPath(secureDir)) {
            fileManager.createDirectoryAtPath(secureDir, true, protectionAttributes, null)
        }

        val data = keyMaterial.toNSData()
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, null)
        }
        check(fileManager.createFileAtPath(path, data, protectionAttributes)) {
            "Failed to persist config encryption key."
        }
        runCatching {
            fileManager.setAttributes(protectionAttributes, path, null)
        }
    }

    private fun ByteArray.toNSData(): NSData {
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
