package com.clouddroidide.android.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.clouddroidide.android.core.security.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resumable HTTP streaming download engine supporting HTTP Range, pause/resume/cancel,
 * live MB/s throughput measurement, and Foreground Service notification updates.
 */
class DownloadManager(
    private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<Map<String, DownloadTaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTaskState>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun enqueue(
        title: String,
        url: String,
        destinationFile: File,
        purpose: DownloadPurpose,
        requiresGitHubAuth: Boolean = false,
        id: String = UUID.randomUUID().toString()
    ): String {
        destinationFile.parentFile?.mkdirs()
        val initial = DownloadTaskState(
            id = id,
            title = title,
            url = url,
            destinationPath = destinationFile.absolutePath,
            purpose = purpose,
            status = DownloadStatus.QUEUED,
            downloadedBytes = File("${destinationFile.absolutePath}.part").takeIf { it.exists() }?.length() ?: 0L,
            requiresGitHubAuth = requiresGitHubAuth
        )
        _tasks.update { it + (id to initial) }
        startServiceForTask(id)
        launchDownload(id)
        return id
    }

    fun pause(id: String) {
        activeJobs.remove(id)?.cancel()
        updateTask(id) { it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L) }
    }

    fun resume(id: String) {
        val task = _tasks.value[id] ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return
        startServiceForTask(id)
        launchDownload(id)
    }

    fun cancel(id: String) {
        activeJobs.remove(id)?.cancel()
        val task = _tasks.value[id]
        if (task != null) {
            File("${task.destinationPath}.part").delete()
            updateTask(id) { it.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0L) }
        }
    }

    private fun startServiceForTask(id: String) {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_TRACK
            putExtra(DownloadForegroundService.EXTRA_TASK_ID, id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun launchDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        val job = scope.launch {
            val task = _tasks.value[id] ?: return@launch
            val destFile = File(task.destinationPath)
            val partFile = File("${task.destinationPath}.part")
            destFile.parentFile?.mkdirs()

            var existingBytes = if (partFile.exists()) partFile.length() else 0L
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    downloadedBytes = existingBytes,
                    errorMessage = null
                )
            }

            try {
                val requestBuilder = Request.Builder()
                    .url(task.url)
                    .header("User-Agent", "CloudDroidIDE-Android/1.0")
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }
                if (task.requiresGitHubAuth) {
                    secureStorage.getGitHubToken()?.let { token ->
                        requestBuilder.header("Authorization", "Bearer $token")
                        requestBuilder.header("Accept", "application/vnd.github+json")
                    }
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 416) {
                        // Requested range not satisfiable -> restart cleanly
                        partFile.delete()
                        existingBytes = 0L
                        throw RestartWithoutRangeException()
                    }
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: error("Empty response body")
                    val supportsResume = response.code == 206
                    if (!supportsResume && existingBytes > 0L) {
                        partFile.delete()
                        existingBytes = 0L
                    }

                    val contentLength = body.contentLength()
                    val totalBytes = when {
                        supportsResume && contentLength > 0L -> existingBytes + contentLength
                        contentLength > 0L -> contentLength
                        else -> -1L
                    }

                    updateTask(id) {
                        it.copy(
                            downloadedBytes = existingBytes,
                            totalBytes = totalBytes,
                            status = DownloadStatus.DOWNLOADING
                        )
                    }

                    val buffer = ByteArray(32 * 1024)
                    var bytesCopied = existingBytes
                    var windowBytes = 0L
                    var lastSpeedTs = System.currentTimeMillis()
                    var currentSpeed = 0L

                    body.byteStream().use { input ->
                        if (supportsResume && partFile.exists()) {
                            RandomAccessFile(partFile, "rw").use { raf ->
                                raf.seek(existingBytes)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    raf.write(buffer, 0, read)
                                    bytesCopied += read
                                    windowBytes += read
                                    val now = System.currentTimeMillis()
                                    val elapsed = now - lastSpeedTs
                                    if (elapsed >= 350L) {
                                        currentSpeed = (windowBytes * 1000L) / elapsed
                                        windowBytes = 0L
                                        lastSpeedTs = now
                                        updateTask(id) {
                                            it.copy(
                                                downloadedBytes = bytesCopied,
                                                totalBytes = totalBytes,
                                                speedBytesPerSec = currentSpeed
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            FileOutputStream(partFile).use { fos ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    fos.write(buffer, 0, read)
                                    bytesCopied += read
                                    windowBytes += read
                                    val now = System.currentTimeMillis()
                                    val elapsed = now - lastSpeedTs
                                    if (elapsed >= 350L) {
                                        currentSpeed = (windowBytes * 1000L) / elapsed
                                        windowBytes = 0L
                                        lastSpeedTs = now
                                        updateTask(id) {
                                            it.copy(
                                                downloadedBytes = bytesCopied,
                                                totalBytes = totalBytes,
                                                speedBytesPerSec = currentSpeed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (destFile.exists()) destFile.delete()
                    if (!partFile.renameTo(destFile)) {
                        partFile.copyTo(destFile, overwrite = true)
                        partFile.delete()
                    }

                    updateTask(id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = destFile.length(),
                            totalBytes = destFile.length(),
                            speedBytesPerSec = 0L
                        )
                    }
                }
            } catch (_: RestartWithoutRangeException) {
                launchDownload(id)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        speedBytesPerSec = 0L,
                        errorMessage = t.message ?: "Download failed"
                    )
                }
            } finally {
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

    private fun updateTask(id: String, transform: (DownloadTaskState) -> DownloadTaskState) {
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            map + (id to transform(current))
        }
    }

    private class RestartWithoutRangeException : Exception()
}
