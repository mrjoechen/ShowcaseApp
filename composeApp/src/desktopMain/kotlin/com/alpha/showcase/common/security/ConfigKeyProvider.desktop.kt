package com.alpha.showcase.common.security

import getConfigDirectory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

actual object ConfigKeyProvider {
    private const val SECURE_DIR = ".secure"
    private const val KEY_FILE_NAME = "config_key_v2.bin"

    actual fun getOrCreateKeyMaterial(): ByteArray {
        val keyFile = resolveKeyFile()

        if (Files.exists(keyFile)) {
            val existing = Files.readAllBytes(keyFile)
            if (existing.size == CONFIG_KEY_SIZE_BYTES) {
                return existing
            }
            Files.deleteIfExists(keyFile)
        }

        val generated = ByteArray(CONFIG_KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        Files.createDirectories(keyFile.parent)
        Files.write(
            keyFile,
            generated,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        applyOwnerOnlyPermissionsIfSupported(keyFile)
        return generated
    }

    private fun resolveKeyFile(): Path {
        val secureDir = Path.of(getConfigDirectory()).resolve(SECURE_DIR)
        return secureDir.resolve(KEY_FILE_NAME)
    }

    private fun applyOwnerOnlyPermissionsIfSupported(path: Path) {
        runCatching {
            if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                return
            }
            val ownerOnly = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            Files.setPosixFilePermissions(path, ownerOnly)
        }
    }
}
