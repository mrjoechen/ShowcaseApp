// 获取 Git 提交数
fun retrieveGitCommitCount(): Int {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
        val output = process.inputStream.reader(Charsets.UTF_8).readText()
        output.trim().toInt()
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }
}

fun retrieveGitHash(): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
        val output = process.inputStream.reader(Charsets.UTF_8).readText()
        output.trim()
    } catch (e: Exception) {
        e.printStackTrace()
        "error"
    }
}

val versionName = findProperty("showcase.versionName") as String
val versionMajor = versionName.split(".")[0].toInt()
val versionMinor = versionName.split(".")[1].toInt()
val versionPatch = versionName.split(".")[2].toInt()
val gitCommitCount = retrieveGitCommitCount()
val gitHash = retrieveGitHash()
val versionCode = gitCommitCount

project.extra["gitCommitCount"] = gitCommitCount
project.extra["gitHash"] = gitHash
project.extra["versionCode"] = versionCode
project.extra["versionName"] = versionName
//1.0.0.123.abc1234
project.extra["versionHash"] = "$versionMajor.$versionMinor.$versionPatch.$gitCommitCount.$gitHash"

project.extra["author"] = findProperty("showcase.author") as String
project.extra["email"] = findProperty("showcase.email") as String
