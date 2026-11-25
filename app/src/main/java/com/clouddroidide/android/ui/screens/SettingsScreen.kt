package com.clouddroidide.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clouddroidide.android.BuildConfig
import com.clouddroidide.android.core.config.IdeSettings
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.updater.ForcedUpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    gitHubRepository: GitHubRepository,
    updateManager: ForcedUpdateManager,
    onOpenAuthDialog: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = IdeSettings())
    val authState by gitHubRepository.authState.collectAsState()
    val updateErr by updateManager.lastCheckError.collectAsState()

    var updateOwner by remember(settings.updateRepoOwner) { mutableStateOf(settings.updateRepoOwner) }
    var updateRepo by remember(settings.updateRepoName) { mutableStateOf(settings.updateRepoName) }
    var rootfsUrl by remember(settings.rootfsUrl) { mutableStateOf(settings.rootfsUrl) }
    var prootUrl by remember(settings.prootUrl) { mutableStateOf(settings.prootUrl) }
    var agySource by remember(settings.agyInstallSource) { mutableStateOf(settings.agyInstallSource) }
    var oauthClientId by remember(settings.oauthClientId) { mutableStateOf(settings.oauthClientId) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = StudioColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("CloudDroid IDE Settings", style = MaterialTheme.typography.titleMedium) }
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
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GitHub Account", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (authState.isAuthenticated) "Signed in as @${authState.username}"
                            else "Not signed in",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (authState.isAuthenticated) StudioColors.AccentGreen else StudioColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenAuthDialog) {
                                Text(if (authState.isAuthenticated) "Switch Account" else "Sign In")
                            }
                            if (authState.isAuthenticated) {
                                OutlinedButton(onClick = { gitHubRepository.logout() }) {
                                    Text("Sign Out")
                                }
                            }
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
                        Text("Zero-Backend Configuration", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = updateOwner, onValueChange = { updateOwner = it }, label = { Text("Update Repo Owner") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = updateRepo, onValueChange = { updateRepo = it }, label = { Text("Update Repo Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = rootfsUrl, onValueChange = { rootfsUrl = it }, label = { Text("AArch64 Rootfs URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = prootUrl, onValueChange = { prootUrl = it }, label = { Text("PRoot Static URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = agySource, onValueChange = { agySource = it }, label = { Text("agy CLI Source (URL or npm pkg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = oauthClientId, onValueChange = { oauthClientId = it }, label = { Text("GitHub Device OAuth Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepository.updateSettings {
                                            it.copy(
                                                updateRepoOwner = updateOwner,
                                                updateRepoName = updateRepo,
                                                rootfsUrl = rootfsUrl,
                                                prootUrl = prootUrl,
                                                agyInstallSource = agySource,
                                                oauthClientId = oauthClientId
                                            )
                                        }
                                        feedback = "Settings saved"
                                    }
                                }
                            ) { Text("Save Settings") }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        updateManager.checkForUpdates()
                                        feedback = updateErr ?: "Checked v${BuildConfig.VERSION_NAME} against $updateOwner/$updateRepo"
                                    }
                                }
                            ) { Text("Check Updates") }
                        }

                        if (!feedback.isNullOrBlank()) {
                            Text(feedback!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentBlue)
                        }
                        Text(
                            text = "App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
