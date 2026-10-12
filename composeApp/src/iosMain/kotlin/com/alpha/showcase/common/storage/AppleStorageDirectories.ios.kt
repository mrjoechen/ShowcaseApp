package com.alpha.showcase.common.storage

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSURL

// iOS keeps Application Support until the app is deleted; never move user data to purgeable storage.
internal actual fun appDataDirectory(caches: NSURL): NSURL = userDirectory(NSApplicationSupportDirectory)!!
