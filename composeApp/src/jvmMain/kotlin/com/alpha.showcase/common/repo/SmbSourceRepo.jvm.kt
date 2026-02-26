package com.alpha.showcase.common.repo

import com.alpha.showcase.common.NativeSmbSourceRepo

actual fun createSmbSourceRepo(): SmbSourceRepo? = NativeSmbSourceRepo()
