package com.alpha.showcase.common.sync

import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.SourceListRepo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.PolymorphicSerializer

/** Remote sources keyed by their unique name; local folders and photo libraries stay on the device. */
internal class SourcesSyncDomain(
    private val repo: SourceListRepo = SourceListRepo(),
) : ConfigSyncDomain {
    override val id: String = ConfigSyncDomains.SOURCES
    override val localEdits: Flow<Unit> get() = SourceListRepo.localEdits
    private val serializer = PolymorphicSerializer(RemoteApi::class)

    override suspend fun read(): SyncSnapshot {
        val sources = repo.readSyncableSources()
        return SyncSnapshot(
            items = sources.associate { it.name to StorageSourceSerializer.sourceJson.encodeToJsonElement(serializer, it) },
            order = sources.map { it.name },
        )
    }

    override suspend fun write(snapshot: SyncSnapshot) {
        val names = (snapshot.order.orEmpty() + snapshot.items.keys).distinct().filter { it in snapshot.items }
        val sources = names.mapNotNull { name ->
            runCatching { StorageSourceSerializer.sourceJson.decodeFromJsonElement(serializer, snapshot.items.getValue(name)) }
                .onFailure { Napier.w("Skipping synced source this version cannot read") }
                .getOrNull()
                ?.takeIf { it.name == name }
        }
        repo.applySyncedSources(sources)
    }
}
