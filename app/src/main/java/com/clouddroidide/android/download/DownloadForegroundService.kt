package com.clouddroidide.android.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clouddroidide.android.CloudDroidApp
import com.clouddroidide.android.MainActivity
import com.clouddroidide.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                intent.getStringExtra(EXTRA_TASK_ID)?.let {
                    CloudDroidApp.container.downloadManager.pause(it)
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                intent.getStringExtra(EXTRA_TASK_ID)?.let {
                    CloudDroidApp.container.downloadManager.resume(it)
                }
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_TASK_ID)?.let {
                    CloudDroidApp.container.downloadManager.cancel(it)
                }
                return START_NOT_STICKY
            }
        }

        val initialNotif = buildNotification(
            title = "CloudDroid Download Manager",
            content = "Preparing download...",
            progress = 0,
            indeterminate = true,
            taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        if (collectorJob == null) {
            collectorJob = scope.launch {
                CloudDroidApp.container.downloadManager.tasks.collectLatest { tasks ->
                    val active = tasks.values.lastOrNull {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                    }
                    if (active != null) {
                        val dlMb = active.downloadedBytes / (1024f * 1024f)
                        val totalText = if (active.totalBytes > 0) {
                            "%.1f / %.1f MB".format(dlMb, active.totalBytes / (1024f * 1024f))
                        } else {
                            "%.1f MB".format(dlMb)
                        }
                        val speedMb = active.speedBytesPerSec / (1024f * 1024f)
                        val text = "$totalText • %.2f MB/s".format(speedMb)
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                title = active.title,
                                content = text,
                                progress = active.progressPercent,
                                indeterminate = active.totalBytes <= 0L,
                                taskId = active.id
                            )
                        )
                    } else {
                        val lastFinished = tasks.values.lastOrNull {
                            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED
                        }
                        if (lastFinished != null) {
                            val msg = if (lastFinished.status == DownloadStatus.COMPLETED) {
                                "Completed: ${lastFinished.title}"
                            } else {
                                "Failed: ${lastFinished.errorMessage ?: "Unknown error"}"
                            }
                            notificationManager.notify(
                                COMPLETION_NOTIFICATION_ID,
                                NotificationCompat.Builder(this@DownloadForegroundService, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .setContentTitle(lastFinished.title)
                                    .setContentText(msg)
                                    .setAutoCancel(true)
                                    .build()
                            )
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean,
        taskId: String?
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(content)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress, indeterminate)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .apply {
            if (taskId != null) {
                val cancelIntent = PendingIntent.getService(
                    this@DownloadForegroundService,
                    taskId.hashCode(),
                    Intent(this@DownloadForegroundService, DownloadForegroundService::class.java).apply {
                        action = ACTION_CANCEL
                        putExtra(EXTRA_TASK_ID, taskId)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                addAction(0, "Cancel", cancelIntent)
            }
        }
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CloudDroid Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live progress for Linux environment, updates, and build artifacts"
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "clouddroid_downloads"
        const val NOTIFICATION_ID = 4101
        const val COMPLETION_NOTIFICATION_ID = 4102
        const val ACTION_TRACK = "com.clouddroidide.android.action.TRACK"
        const val ACTION_PAUSE = "com.clouddroidide.android.action.PAUSE"
        const val ACTION_RESUME = "com.clouddroidide.android.action.RESUME"
        const val ACTION_CANCEL = "com.clouddroidide.android.action.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
