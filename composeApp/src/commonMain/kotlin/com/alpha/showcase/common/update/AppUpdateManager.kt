package com.alpha.showcase.common.update

import com.alpha.showcase.api.github.GithubApi
import com.alpha.showcase.api.github.GithubRelease
import com.alpha.showcase.api.github.GithubReleaseAsset
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.versionName
import getPlatform
import isAndroid
import isIos
import isLinux
import isMacOS
import isWindows

private const val REPO_OWNER = "mrjoechen"
private const val REPO_NAME = "ShowcaseApp"
const val IOS_APP_STORE_URL = "https://apps.apple.com/cn/app/id6744004121"
private val VERSION_REGEX = Regex("""(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
}

data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val digest: String?
)

data class UpdateInstallProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

data class UpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val publishedAt: String?,
    val asset: UpdateAsset?,
    val canInstall: Boolean
)

object AppUpdateManager {

    suspend fun checkForUpdate(): Result<UpdateCheckResult> {
        return runCatching {
            val release = GithubApi().getLatestRelease(REPO_OWNER, REPO_NAME)
            if (!isNewerRelease(release.tagName, versionName)) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.Available(release.toUpdateInfo())
            }
        }
    }

    suspend fun installUpdate(
        info: UpdateInfo,
        onProgress: ((UpdateInstallProgress) -> Unit)? = null
    ): Result<Unit> {
        if (isIos()) {
            return runCatching {
                getPlatform().openUrl(IOS_APP_STORE_URL)
            }
        }

        val asset = info.asset
            ?: return Result.failure(IllegalStateException("No install package found for this platform"))

        return getPlatform().downloadAndInstallUpdate(
            downloadUrl = asset.downloadUrl,
            fileName = asset.name,
            expectedDigest = asset.digest,
            expectedSizeBytes = asset.sizeBytes.takeIf { it > 0 },
            onProgress = onProgress
        )
    }

    private fun GithubRelease.toUpdateInfo(): UpdateInfo {
        val targetAsset = selectAssetForCurrentPlatform(assets)
        val notes = body?.trim().orEmpty()
        return UpdateInfo(
            tagName = tagName,
            releaseTitle = name?.takeIf { it.isNotBlank() } ?: tagName,
            releaseNotes = notes,
            releaseUrl = htmlUrl,
            publishedAt = publishedAt,
            asset = targetAsset?.let { UpdateAsset(it.name, it.browserDownloadUrl, it.size, it.digest) },
            canInstall = isIos() || targetAsset != null
        )
    }

    private fun selectAssetForCurrentPlatform(assets: List<GithubReleaseAsset>): GithubReleaseAsset? {
        if (assets.isEmpty() || isIos()) return null
        val platformAssets = when {
            isAndroid() -> assets.filterByExtensions(".apk")
            isWindows() -> assets.filterByExtensions(".msi", ".exe")
            isMacOS() -> assets.filterByExtensions(".dmg", ".pkg")
            isLinux() -> assets.filterByExtensions(".deb", ".rpm", ".appimage")
            else -> emptyList()
        }
        if (platformAssets.isEmpty()) return null
        return getPlatform().selectUpdateAssetForCurrentArchitecture(platformAssets)
            ?: platformAssets.first()
    }

    private fun List<GithubReleaseAsset>.filterByExtensions(vararg extensions: String): List<GithubReleaseAsset> {
        return filter { asset ->
            extensions.any { extension ->
                asset.name.endsWith(extension, ignoreCase = true)
            }
        }
    }

    private fun isNewerRelease(remoteTag: String, localVersionName: String): Boolean {
        val normalizedRemoteTag = remoteTag.trim()
        val currentCandidates = setOf(
            localVersionName.trim(),
            "v${localVersionName.trim()}"
        )
        if (currentCandidates.any { it.equals(normalizedRemoteTag, ignoreCase = true) }) {
            return false
        }

        val remoteVersion = parseVersion(normalizedRemoteTag)
        val localVersion = parseVersion(localVersionName)
        if (remoteVersion != null && localVersion != null) {
            return compareVersion(remoteVersion, localVersion) > 0
        }

        Log.i("Unable to parse versions(remote=$normalizedRemoteTag, local=$localVersionName), fallback to tag comparison.")
        return true
    }

    private fun parseVersion(raw: String): List<Int>? {
        val match = VERSION_REGEX.find(raw) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        return listOf(major, minor, patch)
    }

    private fun compareVersion(left: List<Int>, right: List<Int>): Int {
        for (index in 0..2) {
            val diff = left[index] - right[index]
            if (diff != 0) return diff
        }
        return 0
    }
}
