package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp

interface FtpSourceRepo : SourceRepository<Ftp, NetworkFile>, FileDirSource<Ftp, NetworkFile>

expect fun createFtpSourceRepo(): FtpSourceRepo?
