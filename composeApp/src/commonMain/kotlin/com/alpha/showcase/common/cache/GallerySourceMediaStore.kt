package com.alpha.showcase.common.cache

data class GalleryMediaInput(
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
)

data class GalleryMediaRecord(
    val sourceName: String,
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: Long,
)

class GallerySourceMediaStore(
    private val database: SourceCacheDatabase = SourceCacheDatabaseProvider.database
) {
    companion object {
        private const val GALLERY_MEDIA_DIR = "gallery_media"
        private const val GALLERY_URI_PREFIX = "gallery://"
    }

    private val dao = database.gallerySourceMediaDao()

    suspend fun addMedias(sourceName: String, medias: List<GalleryMediaInput>): Int {
        if (sourceName.isBlank() || medias.isEmpty()) return 0

        val normalized = medias
            .mapNotNull { input ->
                val mediaUri = input.mediaUri.trim()
                if (mediaUri.isBlank()) {
                    null
                } else {
                    com.alpha.showcase.common.cache.entity.GallerySourceMedia(
                        sourceName = sourceName,
                        mediaUri = mediaUri,
                        displayName = input.displayName.ifBlank { mediaUri.substringAfterLast('/') },
                        mimeType = input.mimeType.lowercase(),
                    )
                }
            }
            .distinctBy { it.mediaUri }

        if (normalized.isEmpty()) return 0

        return dao.insertOrIgnore(normalized).count { it != -1L }
    }

    suspend fun listMedias(sourceName: String): List<GalleryMediaRecord> {
        if (sourceName.isBlank()) return emptyList()
        return dao.getBySource(sourceName).map {
            GalleryMediaRecord(
                sourceName = it.sourceName,
                mediaUri = it.mediaUri,
                displayName = it.displayName,
                mimeType = it.mimeType,
                addedAt = it.addedAt,
            )
        }
    }

    suspend fun restoreMediasFromPersistedFiles(sourceName: String): Int {
        if (sourceName.isBlank()) return 0

        val existing = dao.getBySource(sourceName)
        if (existing.isNotEmpty()) return 0

        return addMedias(sourceName, loadPersistedGalleryMediaInputs(sourceName))
    }

    suspend fun deleteMedias(sourceName: String, mediaUris: List<String>) {
        if (sourceName.isBlank() || mediaUris.isEmpty()) return
        val normalizedUris = mediaUris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedUris.isEmpty()) return

        dao.deleteBySourceAndUris(sourceName, normalizedUris)

        normalizedUris.forEach { mediaUri ->
            deletePersistedGalleryLocalFileIfNeeded(sourceName, mediaUri)
        }
    }

    suspend fun deleteSource(sourceName: String) {
        if (sourceName.isBlank()) return
        dao.deleteBySource(sourceName)
    }
}

internal expect suspend fun loadPersistedGalleryMediaInputs(sourceName: String): List<GalleryMediaInput>

internal expect suspend fun deletePersistedGalleryLocalFileIfNeeded(sourceName: String, mediaUri: String)
