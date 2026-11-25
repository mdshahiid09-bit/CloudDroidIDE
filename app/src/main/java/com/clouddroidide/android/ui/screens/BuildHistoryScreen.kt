package com.clouddroidide.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddroidide.android.build.CloudBuildManager
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadStatus
import com.clouddroidide.android.ui.components.DownloadTaskCard
import com.clouddroidide.android.updater.ApkInstaller
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildHistoryScreen(
    cloudBuildManager: CloudBuildManager,
    gitHubRepository: GitHubRepository,
    settingsRepository: SettingsRepository,
    downloadManager: DownloadManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buildState by cloudBuildManager.state.collectAsState()
    val downloadTasks by downloadManager.tasks.collectAsState()
    val settings by settingsRepository.settingsFlow.collectAsState(
        initial = com.clouddroidide.android.core.config.IdeSettings()
    )
    var repoInput by remember(buildState.selectedRepoFullName, settings.selectedBuildRepo) {
        mutableStateOf(
            buildState.selectedRepoFullName.ifBlank { settings.selectedBuildRepo }
        )
    }
    var branchInput by remember { mutableStateOf("main") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repoInput) {
        if (repoInput.contains("/") && buildState.runs.isEmpty()) {
            cloudBuildManager.refreshRuns(repoInput)
        }
    }

    Scaffold(
        containerColor = StudioColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Background),
                title = { Text("Cloud Build History", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { scope.launch { cloudBuildManager.refreshRuns(repoInput) } }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Runs")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("GitHub Repository (owner/repo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = branchInput,
                                onValueChange = { branchInput = it },
                                label = { Text("Branch") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = repoInput.contains("/"),
                                onClick = {
                                    scope.launch {
                                        settingsRepository.updateSettings { it.copy(selectedBuildRepo = repoInput.trim()) }
                                        cloudBuildManager.triggerAndTrack(repoInput, branchInput)
                                            .onFailure { actionFeedback = it.message }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGreen),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Build")
                            }
                        }
                    }
                }
            }

            val activeDl = downloadTasks.values.lastOrNull { it.status == DownloadStatus.DOWNLOADING }
            if (activeDl != null) {
                item { DownloadTaskCard(task = activeDl, downloadManager = downloadManager) }
            }

            if (!actionFeedback.isNullOrBlank() || !buildState.errorMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = actionFeedback ?: buildState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.AccentRed
                    )
                }
            }

            items(buildState.runs, key = { it.id }) { run ->
                val isSelected = buildState.selectedRun?.id == run.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) StudioColors.SurfaceElevated else StudioColors.Surface,
                    border = BorderStroke(1.dp, if (isSelected) StudioColors.AccentBlue else StudioColors.Divider),
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch { cloudBuildManager.selectRun(run) }
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${run.runNumber} • ${run.name ?: "Workflow"}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Branch: ${run.headBranch ?: "-"} • ${run.status} (${run.conclusion ?: "in progress"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = when (run.conclusion) {
                                    "success" -> StudioColors.AccentGreen
                                    "failure" -> StudioColors.AccentRed
                                    else -> StudioColors.AccentAmber
                                }
                            )
                        }
                    }
                }
            }

            if (buildState.selectedRun != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Surface,
                        border = BorderStroke(1.dp, StudioColors.Divider)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Artifacts & Job Logs", style = MaterialTheme.typography.titleSmall)
                            buildState.artifacts.forEach { art ->
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            cloudBuildManager.downloadAndExtractArtifact(buildState.selectedRun!!.id, art)
                                                .onSuccess { files -> actionFeedback = "Ready: ${files.joinToString { it.name }}" }
                                                .onFailure { err -> actionFeedback = err.message }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download ${art.name}")
                                }
                            }
                            buildState.extractedFiles.filter { it.extension.equals("apk", true) }.forEach { apk ->
                                Button(
                                    onClick = {
                                        if (!ApkInstaller.canRequestPackageInstalls(context)) {
                                            ApkInstaller.openInstallPermissionSettings(context)
                                        } else {
                                            ApkInstaller.launchInstaller(context, apk)
                                                .onFailure { actionFeedback = it.message }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Install ${apk.name}")
                                }
                            }
                            if (buildState.logsText.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = buildState.logsText.takeLast(6000),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = StudioColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
