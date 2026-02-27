package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Sftp

interface SftpSourceRepo : SourceRepository<Sftp, NetworkFile>, FileDirSource<Sftp, NetworkFile>

expect fun createSftpSourceRepo(): SftpSourceRepo?
