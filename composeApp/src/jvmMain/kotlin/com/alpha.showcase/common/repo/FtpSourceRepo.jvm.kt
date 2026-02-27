package com.alpha.showcase.common.repo

import com.alpha.showcase.common.NativeFtpSourceRepo

actual fun createFtpSourceRepo(): FtpSourceRepo? = NativeFtpSourceRepo()
