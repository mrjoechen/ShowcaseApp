import io.github.mrjoechen.Once
import io.github.mrjoechen.OnceTimeUnit

actual fun shouldRunAutomaticUpdateCheck(): Boolean {
    val tag = "check-showcase-update"
    if (Once.beenDone(OnceTimeUnit.DAYS, 1, tag)) {
        return false
    }
    Once.markDone(tag)
    return true
}
