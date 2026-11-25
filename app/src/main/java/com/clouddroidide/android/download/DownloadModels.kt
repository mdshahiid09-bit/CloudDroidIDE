package com.clouddroidide.android.download

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DownloadPurpose {
    LINUX_ROOTFS,
    PROOT_BINARY,
    APP_UPDATE_APK,
    BUILD_ARTIFACT,
    GENERIC
}

data class DownloadTaskState(
    val id: String,
    val title: String,
    val url: String,
    val destinationPath: String,
    val purpose: DownloadPurpose,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val requiresGitHubAuth: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt()
}
