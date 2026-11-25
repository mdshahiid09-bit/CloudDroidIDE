package com.clouddroidide.android.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class InspectedArchive(
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val sizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: List<String>,
    val sha256Fingerprint: String,
    val entriesCount: Int
)

object ApkInspector {
    @Suppress("DEPRECATION")
    suspend fun inspect(context: Context, file: File): Result<InspectedArchive> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists()) { "File not found: ${file.name}" }
            val ext = file.extension.lowercase()
            val entriesCount = ZipFile(file).use { it.size() }
            val fileDigest = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString(":") { "%02X".format(it) }

            if (ext == "apk") {
                val flags = PackageManager.GET_PERMISSIONS or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        PackageManager.GET_SIGNATURES
                    }
                val info: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(
                        file.absolutePath,
                        PackageManager.PackageInfoFlags.of(flags.toLong())
                    )
                } else {
                    context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                }

                val certSha256 = extractApkCertSha256(info) ?: "Unsigned / unavailable (Archive SHA-256: $fileDigest)"
                val vCode = if (info == null) 0L else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
                InspectedArchive(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileType = "APK",
                    sizeBytes = file.length(),
                    packageName = info?.packageName ?: "Unknown",
                    versionName = info?.versionName ?: "Unknown",
                    versionCode = vCode,
                    permissions = info?.requestedPermissions?.toList() ?: emptyList(),
                    sha256Fingerprint = certSha256,
                    entriesCount = entriesCount
                )
            } else {
                // AAB inspection via Zip entries + SHA-256
                val hasBundleManifest = ZipFile(file).use { zip ->
                    zip.getEntry("base/manifest/AndroidManifest.xml") != null
                }
                InspectedArchive(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileType = if (hasBundleManifest) "Android App Bundle (AAB)" else ext.uppercase(),
                    sizeBytes = file.length(),
                    packageName = if (hasBundleManifest) "Bundle base/manifest/AndroidManifest.xml present" else "Unknown",
                    versionName = "Defined in base bundle",
                    versionCode = 0L,
                    permissions = emptyList(),
                    sha256Fingerprint = "File SHA-256: $fileDigest",
                    entriesCount = entriesCount
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractApkCertSha256(info: PackageInfo?): String? {
        if (info == null) return null
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: return null
        val first = sigs.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(first.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }
}

object LogcatReader {
    suspend fun dumpAppLogcat(maxLines: Int = 250): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pid = android.os.Process.myPid()
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid")
                .redirectErrorStream(true)
                .start()
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor()
            lines.takeLast(maxLines)
        }.getOrElse { err ->
            listOf("Unable to read logcat: ${err.message}")
        }
    }
}
