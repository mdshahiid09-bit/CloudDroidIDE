package com.clouddroidide.android.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.clouddroidide.android.BuildConfig
import com.clouddroidide.android.core.config.AppConfig
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadPurpose
import com.clouddroidide.android.download.DownloadStatus
import com.clouddroidide.android.download.DownloadTaskState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.zip.ZipFile

data class UpdateAvailability(
    val currentVersion: String,
    val latestTag: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

object ApkInstaller {
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun verifyApkIntegrity(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null &&
                    zip.getEntry("classes.dex") != null
            }
        }.getOrDefault(false)
    }

    fun launchInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(verifyApkIntegrity(apkFile)) { "Downloaded file is not a valid Android APK package" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

class ForcedUpdateManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val gitHubRepository: GitHubRepository,
    private val downloadManager: DownloadManager
) {
    private val _mandatoryUpdate = MutableStateFlow<UpdateAvailability?>(null)
    val mandatoryUpdate: StateFlow<UpdateAvailability?> = _mandatoryUpdate.asStateFlow()

    private val _activeUpdateTaskId = MutableStateFlow<String?>(null)
    val activeUpdateTaskId: StateFlow<String?> = _activeUpdateTaskId.asStateFlow()

    private val _lastCheckError = MutableStateFlow<String?>(null)
    val lastCheckError: StateFlow<String?> = _lastCheckError.asStateFlow()

    suspend fun checkForUpdates() {
        val settings = settingsRepository.current()
        if (settings.updateRepoOwner.isBlank() || settings.updateRepoName.isBlank()) return
        val result = gitHubRepository.getLatestRelease(
            owner = settings.updateRepoOwner,
            repo = settings.updateRepoName
        )
        result.onSuccess { release ->
            _lastCheckError.value = null
            if (release != null && !release.draft && SemVer.isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                if (apkAsset != null) {
                    _mandatoryUpdate.value = UpdateAvailability(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestTag = release.tagName,
                        releaseNotes = release.body?.takeIf { it.isNotBlank() }
                            ?: "Release ${release.tagName} is available.",
                        apkUrl = apkAsset.browserDownloadUrl,
                        apkFileName = apkAsset.name,
                        apkSizeBytes = apkAsset.size
                    )
                }
            }
        }.onFailure { err ->
            _lastCheckError.value = err.message
        }
    }

    fun startUpdateDownload(): String? {
        val update = _mandatoryUpdate.value ?: return null
        val dir = File(context.filesDir, AppConfig.UPDATES_DIR_NAME)
        val outFile = File(dir, update.apkFileName)
        val taskId = downloadManager.enqueue(
            title = "CloudDroid IDE ${update.latestTag}",
            url = update.apkUrl,
            destinationFile = outFile,
            purpose = DownloadPurpose.APP_UPDATE_APK
        )
        _activeUpdateTaskId.value = taskId
        return taskId
    }

    fun installIfCompleted(taskState: DownloadTaskState?): Result<Unit> {
        if (taskState == null || taskState.status != DownloadStatus.COMPLETED) {
            return Result.failure(IllegalStateException("Update APK download is not completed"))
        }
        return ApkInstaller.launchInstaller(context, File(taskState.destinationPath))
    }
}
