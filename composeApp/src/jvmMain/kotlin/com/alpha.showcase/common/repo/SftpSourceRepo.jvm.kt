package com.alpha.showcase.common.repo

import com.alpha.showcase.common.NativeSftpSourceRepo

actual fun createSftpSourceRepo(): SftpSourceRepo? = NativeSftpSourceRepo()
