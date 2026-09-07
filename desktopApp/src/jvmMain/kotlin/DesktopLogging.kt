import io.github.aakira.napier.DebugAntilog
import java.io.PrintStream
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.SimpleFormatter

internal object DesktopLogging {
    fun configureStandardStreams() {
        // On Windows, Java 21 can use UTF-8 for files but GBK for stdout/stderr.
        // Configure the actual streams as well, including direct IDE launches.
        System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
        System.setErr(PrintStream(System.err, true, Charsets.UTF_8))
    }

    fun createAntilog(): DebugAntilog = DebugAntilog(
        handler = listOf(ConsoleHandler().apply {
            level = Level.ALL
            encoding = Charsets.UTF_8.name()
            formatter = SimpleFormatter()
        }),
    )
}
