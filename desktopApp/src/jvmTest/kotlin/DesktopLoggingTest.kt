import io.github.aakira.napier.LogLevel
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLoggingTest {
    @Test
    fun chineseLogsAndStackTracesRemainUtf8WithWindowsLegacyEncoding() {
        // Fork to exercise stream initialization before JUL/Napier are created,
        // without changing the test runner's global streams or locale.
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Dfile.encoding=GBK",
            "-Dstdout.encoding=GBK",
            "-Dstderr.encoding=GBK",
            "-Duser.language=zh",
            "-Duser.country=CN",
            "-cp", System.getProperty("desktop.test.classpath"),
            "DesktopLoggingProbe",
        ).start()
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Logging probe timed out")
            val stdout = decodeUtf8(process.inputStream.readBytes())
            val stderr = decodeUtf8(process.errorStream.readBytes())
            assertEquals(0, process.exitValue(), stderr)
            assertTrue(stdout.contains("标准输出：你好，Windows！"), stdout)
            assertTrue(stderr.contains("标准错误：中文路径"), stderr)
            assertTrue(stderr.contains("[DEBUG] probe - 中文日志：你好，Windows！"), stderr)
            assertTrue(stderr.contains("[WARN] probe - 资源目录"), stderr)
            assertTrue(stderr.contains("IllegalStateException: 中文异常"), stderr)
            assertTrue(stderr.contains("DesktopLoggingProbe.main"), stderr)
            assertEquals(1, Regex("中文日志：你好，Windows！").findAll(stderr).count())
            assertFalse(stderr.contains('\uFFFD'), stderr)
        } finally {
            process.destroyForcibly()
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

object DesktopLoggingProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        DesktopLogging.configureStandardStreams()
        val antilog = DesktopLogging.createAntilog()
        println("标准输出：你好，Windows！")
        System.err.println("标准错误：中文路径")
        antilog.log(LogLevel.DEBUG, "probe", null, "中文日志：你好，Windows！")
        antilog.log(LogLevel.WARNING, "probe", null, "资源目录")
        antilog.log(LogLevel.ERROR, "probe", IllegalStateException("中文异常"), "异常堆栈")
    }
}
