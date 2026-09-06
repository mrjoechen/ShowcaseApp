package com.alpha.ai.imagegeneration

import java.io.InputStream
import okio.Source
import okio.source

/** Compatibility adapter for Android and JVM integrations that already supply streams. */
interface InputStreamImageSource : ImageSource {
    fun openStream(): InputStream
    override fun openSource(): Source = openStream().source()
}
