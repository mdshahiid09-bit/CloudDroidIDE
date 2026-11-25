package com.clouddroidide.android.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.clouddroidide.android.build.CloudBuildManager
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.security.SecureStorage
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.data.remote.GitHubRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayStoreScreen(
    secureStorage: SecureStorage,
    gitHubRepository: GitHubRepository,
    cloudBuildManager: CloudBuildManager,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(
        initial = com.clouddroidide.android.core.config.IdeSettings()
    )
    val buildState by cloudBuildManager.state.collectAsState()

    var repoInput by remember(settings.selectedBuildRepo) { mutableStateOf(settings.selectedBuildRepo) }
    var branchInput by remember { mutableStateOf("main") }
    var serviceAccountJson by remember { mutableStateOf(secureStorage.getPlayServiceAccountJson() ?: "") }
    var selectedTrack by remember { mutableStateOf("internal") }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            if (text.contains("private_key") && text.contains("client_email")) {
                serviceAccountJson = text
                secureStorage.setPlayServiceAccountJson(text)
                isError = false
                statusMsg = "Imported & encrypted Google Play Service Account JSON"
            } else {
                isError = true
                statusMsg = "Selected file is not a valid Google Play Service Account JSON"
            }
        }
    }

    Scaffold(
        containerColor = StudioColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Background),
                title = { Text("Google Play Store Release", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!statusMsg.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioColors.SurfaceElevated,
                        border = BorderStroke(1.dp, if (isError) StudioColors.AccentRed else StudioColors.AccentGreen)
                    ) {
                        Text(
                            text = statusMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) StudioColors.AccentRed else StudioColors.AccentGreen,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Service Account JSON Secret", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("Target Repository (owner/repo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { jsonPicker.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (serviceAccountJson.isBlank()) "Import Service Account JSON" else "Replace Service Account JSON")
                        }
                        if (serviceAccountJson.isNotBlank()) {
                            Text(
                                text = "Service Account JSON stored locally (${serviceAccountJson.length} chars)",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.AccentGreen
                            )
                        }
                        Button(
                            enabled = serviceAccountJson.isNotBlank() && repoInput.contains("/"),
                            onClick = {
                                val parts = repoInput.trim().split("/")
                                if (parts.size != 2) return@Button
                                scope.launch {
                                    isError = false
                                    gitHubRepository.pushEncryptedSecret(
                                        parts[0],
                                        parts[1],
                                        "PLAY_STORE_SERVICE_ACCOUNT_JSON",
                                        serviceAccountJson
                                    ).onSuccess {
                                        statusMsg = "Encrypted & pushed PLAY_STORE_SERVICE_ACCOUNT_JSON to $repoInput"
                                    }.onFailure { err ->
                                        isError = true
                                        statusMsg = err.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Push Secret to GitHub")
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. Select Track & Trigger Play Release", style = MaterialTheme.typography.titleSmall)
                        listOf("internal", "alpha", "beta", "production").forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTrack = track }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedTrack == track, onClick = { selectedTrack = track })
                                Text(track.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Git Branch") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = repoInput.contains("/"),
                            onClick = {
                                scope.launch {
                                    isError = false
                                    settingsRepository.updateSettings { it.copy(selectedBuildRepo = repoInput.trim()) }
                                    cloudBuildManager.triggerAndTrack(repoInput, branchInput, playTrack = selectedTrack)
                                        .onSuccess { statusMsg = "Triggered Play Store ($selectedTrack) release build" }
                                        .onFailure { err -> isError = true; statusMsg = err.message }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Build Signed AAB & Publish ($selectedTrack)")
                        }
                    }
                }
            }

            val aabFiles = buildState.extractedFiles.filter { it.extension.equals("aab", true) }
            if (aabFiles.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Surface,
                        border = BorderStroke(1.dp, StudioColors.Divider)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3. Direct Signed AAB Export", style = MaterialTheme.typography.titleSmall)
                            aabFiles.forEach { aab ->
                                OutlinedButton(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            aab
                                        )
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Export ${aab.name}"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export ${aab.name} for Play Console")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
