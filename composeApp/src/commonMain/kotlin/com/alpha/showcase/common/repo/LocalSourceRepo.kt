package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.utils.ToastUtil
import getPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


class LocalSourceRepo: SourceRepository<Local, NetworkFile> {
    override suspend fun getItem(remoteApi: Local): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Local,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        return withContext(Dispatchers.Default){
            try {
                val platform = getPlatform()
                val pendingDirectories = ArrayDeque<String>()
                val visitedDirectories = mutableSetOf<String>()
                val files = mutableListOf<NetworkFile>()
                pendingDirectories.add(remoteApi.path)

                while (pendingDirectories.isNotEmpty()) {
                    ensureActive()
                    val path = pendingDirectories.removeFirst()
                    if (recursive && !visitedDirectories.add(platform.directoryTraversalKey(path))) {
                        continue
                    }
                    platform.listFiles(path).forEach { localFile ->
                        ensureActive()
                        // Discover directories before filtering: playback filters only accept media.
                        if (recursive && localFile.isDirectory) {
                            pendingDirectories.add(localFile.path)
                        } else {
                            val file = NetworkFile(
                                remoteApi,
                                localFile.path,
                                localFile.fileName,
                                localFile.isDirectory,
                                localFile.size,
                                localFile.mimeType,
                                localFile.modTime,
                            )
                            if (filter == null || filter(file)) files.add(file)
                        }
                    }
                }
                Result.success(files)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ToastUtil.error("Folder not found: ${remoteApi.path}")
                Result.failure(t)
            }
        }
    }

}
