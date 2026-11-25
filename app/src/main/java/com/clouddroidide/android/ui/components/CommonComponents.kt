package com.clouddroidide.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clouddroidide.android.core.config.IdeSettings
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.data.remote.model.DeviceCodeResponse
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadStatus
import com.clouddroidide.android.download.DownloadTaskState
import com.clouddroidide.android.updater.ApkInstaller
import com.clouddroidide.android.updater.ForcedUpdateManager
import com.clouddroidide.android.updater.UpdateAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DownloadTaskCard(
    task: DownloadTaskState,
    downloadManager: DownloadManager,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = task.progressFraction,
        label = "dl_progress"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.Surface,
        border = BorderStroke(1.dp, StudioColors.Divider)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = "Download",
                    tint = StudioColors.AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${task.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.AccentBlue
                )
            }
            Spacer(Modifier.height(8.dp))
            if (task.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = StudioColors.AccentBlue,
                    trackColor = StudioColors.SurfaceElevated
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = StudioColors.AccentBlue,
                    trackColor = StudioColors.SurfaceElevated
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dlMb = task.downloadedBytes / (1024f * 1024f)
                val totText = if (task.totalBytes > 0) "%.2f MB".format(task.totalBytes / (1024f * 1024f)) else "--"
                val speedMb = task.speedBytesPerSec / (1024f * 1024f)
                Text(
                    text = "%.2f / %s  •  %.2f MB/s  •  %s".format(dlMb, totText, speedMb, task.status.name),
                    style = MaterialTheme.typography.bodySmall
                )
                Row {
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = { downloadManager.pause(task.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pause", tint = StudioColors.TextPrimary)
                        }
                    } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                        IconButton(onClick = { downloadManager.resume(task.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume", tint = StudioColors.AccentGreen)
                        }
                    }
                    if (task.status != DownloadStatus.COMPLETED && task.status != DownloadStatus.CANCELLED) {
                        IconButton(onClick = { downloadManager.cancel(task.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel", tint = StudioColors.AccentRed)
                        }
                    }
                }
            }
            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.AccentRed,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ForcedUpdateDialog(
    update: UpdateAvailability,
    activeTask: DownloadTaskState?,
    downloadManager: DownloadManager,
    updateManager: ForcedUpdateManager
) {
    val context = LocalContext.current
    var installError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { /* Non-dismissible mandatory update */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.Background),
            color = StudioColors.Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = "Required Update",
                    tint = StudioColors.AccentBlue,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Mandatory Update Required",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Current: v${update.currentVersion}  ->  Latest: ${update.latestTag} (%.2f MB)".format(
                        update.apkSizeBytes / (1024f * 1024f)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.AccentBlue
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Text(
                        text = update.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (activeTask != null) {
                    DownloadTaskCard(task = activeTask, downloadManager = downloadManager)
                    Spacer(Modifier.height(12.dp))
                }

                if (!installError.isNullOrBlank()) {
                    Text(
                        text = installError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.AccentRed,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                when (activeTask?.status) {
                    DownloadStatus.COMPLETED -> {
                        Button(
                            onClick = {
                                if (!ApkInstaller.canRequestPackageInstalls(context)) {
                                    ApkInstaller.openInstallPermissionSettings(context)
                                } else {
                                    updateManager.installIfCompleted(activeTask)
                                        .onFailure { installError = it.message }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGreen)
                        ) {
                            Text("Install ${update.latestTag}")
                        }
                    }
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        OutlinedButton(
                            onClick = { downloadManager.pause(activeTask.id) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Pause Download")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { updateManager.startUpdateDownload() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Update Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubAuthDialog(
    gitHubRepository: GitHubRepository,
    settings: IdeSettings,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var patInput by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<DeviceCodeResponse?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = StudioColors.AccentBlue)
                Spacer(Modifier.width(8.dp))
                Text("GitHub Authentication")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioColors.Surface
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("PAT Token") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Device OAuth") })
                }
                Spacer(Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Text(
                        text = "Stored securely via Android Keystore EncryptedSharedPreferences. Requires repo and workflow scopes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = { Text("GitHub Personal Access Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (settings.oauthClientId.isBlank()) {
                        Text(
                            text = "No GitHub OAuth Client ID is configured yet. Add your OAuth App Client ID in Settings or use PAT login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.AccentAmber
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            onDismiss()
                            onOpenSettings()
                        }) {
                            Text("Configure Client ID in Settings")
                        }
                    } else if (deviceCode == null) {
                        Text(
                            text = "Sign in using GitHub Device Flow OAuth (Client ID: ${settings.oauthClientId.take(10)}...).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    isError = false
                                    gitHubRepository.startDeviceFlow(settings.oauthClientId)
                                        .onSuccess { dc ->
                                            deviceCode = dc
                                            statusMessage = "Enter code on GitHub, polling for authorization..."
                                            val intervalMs = (dc.interval.coerceAtLeast(5)) * 1000L
                                            while (deviceCode != null) {
                                                delay(intervalMs)
                                                val pollRes = gitHubRepository.pollDeviceToken(
                                                    settings.oauthClientId,
                                                    dc.deviceCode
                                                )
                                                val user = pollRes.getOrNull()
                                                if (user != null) {
                                                    statusMessage = "Authenticated as ${user.login}"
                                                    isBusy = false
                                                    onDismiss()
                                                    break
                                                } else if (pollRes.isFailure) {
                                                    isError = true
                                                    statusMessage = pollRes.exceptionOrNull()?.message
                                                    isBusy = false
                                                    break
                                                }
                                            }
                                        }
                                        .onFailure { err ->
                                            isError = true
                                            statusMessage = err.message
                                            isBusy = false
                                        }
                                }
                            }
                        ) {
                            Text("Request GitHub Device Code")
                        }
                    } else {
                        val dc = deviceCode!!
                        Text("1. Copy your one-time code:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = StudioColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioColors.Divider)
                        ) {
                            Text(
                                text = dc.userCode,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StudioColors.AccentBlue,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))
                            )
                        }) {
                            Text("Open ${dc.verificationUri}")
                        }
                    }
                }

                if (isBusy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying with GitHub...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) StudioColors.AccentRed else StudioColors.AccentGreen
                    )
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    enabled = !isBusy && patInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            isError = false
                            statusMessage = null
                            gitHubRepository.authenticateWithPat(patInput)
                                .onSuccess { user ->
                                    isBusy = false
                                    statusMessage = "Signed in as ${user.login}"
                                    onDismiss()
                                }
                                .onFailure { err ->
                                    isBusy = false
                                    isError = true
                                    statusMessage = err.message ?: "Authentication failed"
                                }
                        }
                    }
                ) {
                    Text("Sign In")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                deviceCode = null
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

@Composable
fun QuickExtraKeysRow(
    keys: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { (label, action) ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = StudioColors.SurfaceElevated,
                border = BorderStroke(1.dp, StudioColors.Divider),
                modifier = Modifier.clickable(onClick = action)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
