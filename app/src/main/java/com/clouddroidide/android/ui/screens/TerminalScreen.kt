package com.clouddroidide.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.terminal.LinuxEnvPhase
import com.clouddroidide.android.terminal.LinuxEnvironmentManager
import com.clouddroidide.android.terminal.TerminalSession
import com.clouddroidide.android.ui.components.DownloadTaskCard
import com.clouddroidide.android.ui.components.QuickExtraKeysRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    linuxEnvironmentManager: LinuxEnvironmentManager,
    downloadManager: DownloadManager,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = StudioColors.AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("AArch64 PRoot Terminal", style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { padding ->
        EmbeddedLinuxTerminalPanel(
            linuxEnvironmentManager = linuxEnvironmentManager,
            downloadManager = downloadManager,
            settingsRepository = settingsRepository,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
fun EmbeddedLinuxTerminalPanel(
    linuxEnvironmentManager: LinuxEnvironmentManager,
    downloadManager: DownloadManager,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val envState by linuxEnvironmentManager.state.collectAsState()
    val downloadTasks by downloadManager.tasks.collectAsState()
    val settings by settingsRepository.settingsFlow.collectAsState(
        initial = com.clouddroidide.android.core.config.IdeSettings()
    )

    val bootstrapLogs = remember { mutableStateListOf<String>() }
    var agyInput by remember(settings.agyInstallSource) { mutableStateOf(settings.agyInstallSource) }
    var commandInput by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var agyStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        linuxEnvironmentManager.refreshState()
    }

    LaunchedEffect(envState.phase) {
        if (envState.phase == LinuxEnvPhase.READY && session == null) {
            val s = linuxEnvironmentManager.createInteractiveSession()
            s.start(rows = 28, cols = 88)
            session = s
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.stop()
            session = null
        }
    }

    Column(modifier = modifier.background(StudioColors.Background)) {
        if (envState.phase != LinuxEnvPhase.READY) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AArch64 Alpine + PRoot Bootstrap", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Status: ${envState.statusDetail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (envState.phase == LinuxEnvPhase.ERROR) StudioColors.AccentRed else StudioColors.AccentAmber
                        )
                        Text(
                            text = "Installs official Alpine AArch64 minirootfs, PRoot, bash, git, gh, nodejs, python3, and curl.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = agyInput,
                            onValueChange = { agyInput = it },
                            label = { Text("agy CLI Source (optional: .sh / .tar.gz / URL / npm pkg)") },
                            placeholder = { Text("https://.../install.sh or @scope/agy-cli") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = envState.phase == LinuxEnvPhase.NOT_INSTALLED ||
                                envState.phase == LinuxEnvPhase.ERROR ||
                                envState.phase == LinuxEnvPhase.EXTRACTING,
                            onClick = {
                                bootstrapLogs.clear()
                                scope.launch {
                                    settingsRepository.updateSettings { it.copy(agyInstallSource = agyInput.trim()) }
                                    linuxEnvironmentManager.installAndBootstrap { line ->
                                        scope.launch(Dispatchers.Main.immediate) {
                                            bootstrapLogs.add(line)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (envState.phase == LinuxEnvPhase.ERROR) "Retry Download & Bootstrap"
                                else "Download & Bootstrap AArch64 Linux"
                            )
                        }
                    }
                }

                envState.downloadTaskId?.let { id ->
                    downloadTasks[id]?.let { task ->
                        DownloadTaskCard(task = task, downloadManager = downloadManager)
                    }
                }

                if (bootstrapLogs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Surface,
                        border = BorderStroke(1.dp, StudioColors.Divider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = bootstrapLogs.takeLast(120).joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = StudioColors.TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val activeSession = session
            val terminalText by (activeSession?.screenText ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow("Starting PRoot PTY session...\n")
            }).collectAsState()
            val scrollState = rememberScrollState()

            LaunchedEffect(terminalText.length) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            // Compact top bar for agy setup or session restart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioColors.Surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (envState.hasAgy) "Alpine PRoot • agy ready" else "Alpine PRoot • bash/git/gh",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                if (!envState.hasAgy) {
                    OutlinedButton(
                        onClick = {
                            if (agyInput.isBlank()) {
                                agyStatus = "Set agy URL or npm package in Settings first"
                            } else {
                                scope.launch {
                                    agyStatus = "Installing agy..."
                                    linuxEnvironmentManager.installAgyCli(agyInput) { line ->
                                        scope.launch(Dispatchers.Main.immediate) {
                                            agyStatus = line
                                        }
                                    }.onSuccess {
                                        agyStatus = "agy installed"
                                    }.onFailure { err ->
                                        agyStatus = err.message
                                    }
                                }
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Install agy", fontSize = 11.sp)
                    }
                }
                IconButton(
                    onClick = { activeSession?.clearScreen() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.ClearAll, contentDescription = "Clear", tint = StudioColors.TextSecondary)
                }
                IconButton(
                    onClick = {
                        session?.stop()
                        val s = linuxEnvironmentManager.createInteractiveSession()
                        s.start()
                        session = s
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Restart Shell", tint = StudioColors.TextSecondary)
                }
            }

            if (!agyStatus.isNullOrBlank()) {
                Text(
                    text = agyStatus!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.AccentBlue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }

            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(10.dp)
            ) {
                Text(
                    text = terminalText.ifEmpty { "$ " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = StudioColors.TextPrimary
                )
            }

            QuickExtraKeysRow(
                keys = listOf(
                    "ESC" to { activeSession?.sendRaw("\u001B") },
                    "TAB" to { activeSession?.sendRaw("\t") },
                    "^C" to { activeSession?.sendControlChar('C') },
                    "^D" to { activeSession?.sendControlChar('D') },
                    "↑" to { activeSession?.sendRaw("\u001B[A") },
                    "↓" to { activeSession?.sendRaw("\u001B[B") },
                    "agy" to { commandInput = if (commandInput.isBlank()) "agy " else "$commandInput agy" },
                    "git status" to { activeSession?.sendCommand("git status") },
                    "clear" to { activeSession?.clearScreen(); activeSession?.sendCommand("clear") }
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioColors.Surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter Linux / agy command...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val cmd = commandInput
                        commandInput = ""
                        activeSession?.sendCommand(cmd)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Run Command",
                        tint = StudioColors.AccentGreen
                    )
                }
            }
        }
    }
}
