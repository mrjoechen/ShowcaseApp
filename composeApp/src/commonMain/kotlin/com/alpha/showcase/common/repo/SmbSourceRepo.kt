package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb

interface SmbSourceRepo : SourceRepository<Smb, NetworkFile>,
    FileDirSource<Smb, NetworkFile>,
    BatchSourceRepository<Smb, NetworkFile>

expect fun createSmbSourceRepo(): SmbSourceRepo?
