package com.clouddroidide.android.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.crypto.KeystoreGenerator
import com.clouddroidide.android.core.crypto.KeystoreParams
import com.clouddroidide.android.core.security.SecureStorage
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.data.remote.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreSecretsScreen(
    keystoresDir: File,
    secureStorage: SecureStorage,
    gitHubRepository: GitHubRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by gitHubRepository.authState.collectAsState()
    val settings by settingsRepository.settingsFlow.collectAsState(
        initial = com.clouddroidide.android.core.config.IdeSettings()
    )

    var keystoreFiles by remember { mutableStateOf(keystoresDir.listFiles()?.toList().orEmpty()) }
    var selectedFile by remember { mutableStateOf(keystoreFiles.firstOrNull()) }

    var fileName by remember { mutableStateOf("release.jks") }
    var alias by remember { mutableStateOf("clouddroid") }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var validityYears by remember { mutableStateOf("25") }
    var cn by remember { mutableStateOf("CloudDroid Developer") }
    var ou by remember { mutableStateOf("Mobile") }
    var org by remember { mutableStateOf("CloudDroid") }
    var locality by remember { mutableStateOf("Bengaluru") }
    var state by remember { mutableStateOf("KA") }
    var country by remember { mutableStateOf("IN") }

    var targetRepo by remember(settings.selectedBuildRepo) { mutableStateOf(settings.selectedBuildRepo) }
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }

    var importTargetFile by remember { mutableStateOf<File?>(null) }
    var importAlias by remember { mutableStateOf("") }
    var importStorePass by remember { mutableStateOf("") }
    var importKeyPass by remember { mutableStateOf("") }

    fun reloadKeystores() {
        keystoreFiles = keystoresDir.listFiles { f ->
            f.isFile && (f.extension == "jks" || f.extension == "p12" || f.extension == "keystore")
        }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (selectedFile == null || selectedFile?.exists() != true) {
            selectedFile = keystoreFiles.firstOrNull()
        }
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            gitHubRepository.listRepos().onSuccess { list ->
                repos = list.map { it.fullName }
                if (targetRepo.isBlank() && repos.isNotEmpty()) {
                    targetRepo = repos.first()
                }
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val dest = File(keystoresDir, "imported_${System.currentTimeMillis()}.jks")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            importTargetFile = dest
            reloadKeystores()
        }
    }

    Scaffold(
        containerColor = StudioColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Background),
                title = { Text("Keystores & GitHub Secrets", style = MaterialTheme.typography.titleMedium) }
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

            // Generate / Import Section
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Generate Release Keystore", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import .jks")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("File Name") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Key Alias") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = storePassword, onValueChange = { storePassword = it }, label = { Text("Store Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = keyPassword, onValueChange = { keyPassword = it }, label = { Text("Key Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cn, onValueChange = { cn = it }, label = { Text("CN (Name)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("Org (O)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = ou, onValueChange = { ou = it }, label = { Text("Unit (OU)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = locality, onValueChange = { locality = it }, label = { Text("City (L)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State (ST)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country (C)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = validityYears, onValueChange = { validityYears = it }, label = { Text("Years") }, singleLine = true, modifier = Modifier.weight(1f))
                        }

                        Button(
                            enabled = alias.isNotBlank() && storePassword.length >= 6 && keyPassword.length >= 6,
                            onClick = {
                                scope.launch {
                                    isError = false
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            KeystoreGenerator.generateKeystore(
                                                keystoresDir,
                                                KeystoreParams(
                                                    fileName = fileName,
                                                    alias = alias,
                                                    keystorePassword = storePassword,
                                                    keyPassword = keyPassword,
                                                    validityYears = validityYears.toIntOrNull() ?: 25,
                                                    commonName = cn,
                                                    organizationalUnit = ou,
                                                    organization = org,
                                                    locality = locality,
                                                    state = state,
                                                    country = country
                                                )
                                            )
                                        }
                                    }.onSuccess { info ->
                                        secureStorage.saveKeystoreMeta(alias.trim(), storePassword, keyPassword, info.fileName)
                                        reloadKeystores()
                                        selectedFile = info.file
                                        statusMsg = "Generated ${info.fileName}\nSHA-256: ${info.sha256Fingerprint}"
                                    }.onFailure { err ->
                                        isError = true
                                        statusMsg = err.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Real PKCS12/JKS Keystore")
                        }
                    }
                }
            }

            // Saved Keystores
            item {
                Text("LOCAL KEYSTORES (${keystoreFiles.size})", style = MaterialTheme.typography.labelMedium, color = StudioColors.TextSecondary)
            }

            items(keystoreFiles, key = { it.absolutePath }) { ksFile ->
                val isSelected = selectedFile?.absolutePath == ksFile.absolutePath
                val meta = secureStorage.getKeystoreMeta(ksFile.name)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) StudioColors.SurfaceElevated else StudioColors.Surface,
                    border = BorderStroke(1.dp, if (isSelected) StudioColors.AccentBlue else StudioColors.Divider),
                    modifier = Modifier.fillMaxWidth().clickable { selectedFile = ksFile }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = StudioColors.AccentAmber)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ksFile.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Alias: ${meta?.first ?: "unknown"} • ${ksFile.length()} bytes",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            ksFile.delete()
                            secureStorage.removeKeystoreMeta(ksFile.name)
                            reloadKeystores()
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Keystore", tint = StudioColors.AccentRed)
                        }
                    }
                }
            }

            // Push Secrets to GitHub via Libsodium SealedBox
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Push Signing Secrets to GitHub (Libsodium SealedBox)", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = targetRepo,
                            onValueChange = { targetRepo = it },
                            label = { Text("Target Repository (owner/repo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (repos.isNotEmpty()) {
                            repos.take(4).forEach { r ->
                                Text(
                                    text = r,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioColors.AccentBlue,
                                    modifier = Modifier.clickable { targetRepo = r }.padding(vertical = 2.dp)
                                )
                            }
                        }
                        Button(
                            enabled = !isPushing && selectedFile != null && targetRepo.contains("/") && authState.isAuthenticated,
                            onClick = {
                                val file = selectedFile ?: return@Button
                                val meta = secureStorage.getKeystoreMeta(file.name)
                                if (meta == null) {
                                    isError = true
                                    statusMsg = "No saved password/alias metadata for ${file.name}"
                                    return@Button
                                }
                                val parts = targetRepo.trim().split("/")
                                if (parts.size != 2) return@Button
                                scope.launch {
                                    isPushing = true
                                    isError = false
                                    runCatching {
                                        settingsRepository.updateSettings { it.copy(selectedBuildRepo = targetRepo.trim()) }
                                        val b64 = withContext(Dispatchers.IO) { KeystoreGenerator.toBase64(file) }
                                        gitHubRepository.pushEncryptedSecret(parts[0], parts[1], "KEYSTORE_BASE64", b64).getOrThrow()
                                        gitHubRepository.pushEncryptedSecret(parts[0], parts[1], "KEYSTORE_PASSWORD", meta.second).getOrThrow()
                                        gitHubRepository.pushEncryptedSecret(parts[0], parts[1], "KEY_ALIAS", meta.first).getOrThrow()
                                        gitHubRepository.pushEncryptedSecret(parts[0], parts[1], "KEY_PASSWORD", meta.third).getOrThrow()
                                    }.onSuccess {
                                        statusMsg = "Encrypted & pushed KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD to $targetRepo"
                                    }.onFailure { err ->
                                        isError = true
                                        statusMsg = err.message
                                    }
                                    isPushing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isPushing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Encrypt & Push 4 Signing Secrets")
                        }
                    }
                }
            }
        }
    }

    if (importTargetFile != null) {
        val f = importTargetFile!!
        AlertDialog(
            onDismissRequest = { importTargetFile = null },
            containerColor = StudioColors.Surface,
            title = { Text("Imported Keystore Credentials") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = importAlias, onValueChange = { importAlias = it }, label = { Text("Key Alias") }, singleLine = true)
                    OutlinedTextField(value = importStorePass, onValueChange = { importStorePass = it }, label = { Text("Keystore Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(value = importKeyPass, onValueChange = { importKeyPass = it }, label = { Text("Key Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (verifiedAlias, fp) = KeystoreGenerator.verifyKeystore(f.readBytes(), importStorePass)
                        val chosenAlias = importAlias.ifBlank { verifiedAlias ?: "" }
                        if (chosenAlias.isBlank()) {
                            isError = true
                            statusMsg = "Could not verify keystore password or alias"
                        } else {
                            secureStorage.saveKeystoreMeta(chosenAlias, importStorePass, importKeyPass, f.name)
                            selectedFile = f
                            statusMsg = "Verified & imported ${f.name} ($chosenAlias) SHA-256: ${fp ?: "--"}"
                        }
                        importTargetFile = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { importTargetFile = null }) { Text("Cancel") }
            }
        )
    }
}
