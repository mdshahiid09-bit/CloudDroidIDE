package com.clouddroidide.android.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.clouddroidide.android.build.CloudBuildManager
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.core.ui.StudioIcons
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.terminal.LinuxEnvironmentManager
import com.clouddroidide.android.ui.components.QuickExtraKeysRow
import com.clouddroidide.android.ui.components.SoraCodeEditor
import com.clouddroidide.android.ui.components.SoraEditorController
import com.clouddroidide.android.updater.ApkInstaller
import com.clouddroidide.android.utils.ApkInspector
import com.clouddroidide.android.utils.AppConfigEditor
import com.clouddroidide.android.utils.InspectedArchive
import com.clouddroidide.android.utils.LogcatReader
import com.clouddroidide.android.workspace.AppGradleConfig
import com.clouddroidide.android.workspace.DiagnosticSeverity
import com.clouddroidide.android.workspace.FileNode
import com.clouddroidide.android.workspace.GitEngine
import com.clouddroidide.android.workspace.GitRepoStatus
import com.clouddroidide.android.workspace.OpenFileTab
import com.clouddroidide.android.workspace.ProjectTemplateGenerator
import com.clouddroidide.android.workspace.ProjectWorkspaceRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeWorkspaceScreen(
    projectDir: File,
    triggerCloudBuildOnOpen: Boolean,
    workspaceRepository: ProjectWorkspaceRepository,
    linuxEnvironmentManager: LinuxEnvironmentManager,
    downloadManager: DownloadManager,
    settingsRepository: SettingsRepository,
    gitHubRepository: GitHubRepository,
    gitEngine: GitEngine,
    cloudBuildManager: CloudBuildManager,
    onOpenSettings: () -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val editorController = remember { SoraEditorController() }

    val openTabs = remember { mutableStateListOf<OpenFileTab>() }
    var activeTabIndex by remember { mutableIntStateOf(-1) }
    var expandedDirs by remember { mutableStateOf(setOf(projectDir.absolutePath)) }
    var fileNodes by remember { mutableStateOf(emptyList<FileNode>()) }

    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    var bottomSheetState by remember { mutableIntStateOf(1) } // 0=collapsed, 1=normal, 2=expanded
    var bottomTab by remember { mutableIntStateOf(0) } // 0=Terminal, 1=Build Logs, 2=Logcat, 3=Diagnostics

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showGitDialog by remember { mutableStateOf(false) }
    var showAppConfigDialog by remember { mutableStateOf(false) }
    var showApkInspectorDialog by remember { mutableStateOf(false) }
    var showCloudBuildDialog by remember { mutableStateOf(false) }

    var fileCrudAction by remember { mutableStateOf<Pair<String, File>?>(null) }
    var crudInput by remember { mutableStateOf("") }
    var toastStatus by remember { mutableStateOf<String?>(null) }

    val buildState by cloudBuildManager.state.collectAsState()
    var logcatLines by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshTree() {
        fileNodes = workspaceRepository.listFileTree(projectDir, expandedDirs)
    }

    fun openFileInEditor(file: File, targetLine: Int? = null, targetCol: Int = 1) {
        if (file.isDirectory || !file.exists()) return
        val existingIdx = openTabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
        } else {
            val content = runCatching { file.readText() }.getOrElse { "" }
            openTabs.add(OpenFileTab(file = file, content = content, isModified = false))
            activeTabIndex = openTabs.lastIndex
        }
        if (targetLine != null) {
            scope.launch {
                kotlinx.coroutines.delay(150L)
                editorController.jumpToLine(targetLine, targetCol)
            }
        }
    }

    fun saveCurrentFile() {
        val tab = openTabs.getOrNull(activeTabIndex) ?: return
        runCatching {
            tab.file.writeText(tab.content)
            openTabs[activeTabIndex] = tab.copy(isModified = false)
            toastStatus = "Saved ${tab.file.name}"
        }.onFailure {
            toastStatus = "Save failed: ${it.message}"
        }
    }

    LaunchedEffect(projectDir.absolutePath) {
        expandedDirs = setOf(
            projectDir.absolutePath,
            File(projectDir, "app").absolutePath,
            File(projectDir, "app/src").absolutePath,
            File(projectDir, "app/src/main").absolutePath
        )
        refreshTree()

        // Auto-open MainActivity or build.gradle.kts if available
        val mainKt = projectDir.walkTopDown().firstOrNull { it.isFile && it.name == "MainActivity.kt" }
        val mainJava = projectDir.walkTopDown().firstOrNull { it.isFile && it.name == "MainActivity.java" }
        val gradleKts = File(projectDir, "app/build.gradle.kts").takeIf { it.exists() }
        val initialFile = mainKt ?: mainJava ?: gradleKts ?: fileNodes.firstOrNull { !it.isDirectory }?.file
        if (initialFile != null) {
            openFileInEditor(initialFile)
        }

        workspaceRepository.parseGitRemoteOwnerRepo(projectDir)?.let { (owner, repo) ->
            cloudBuildManager.setRepo("$owner/$repo")
        }

        if (triggerCloudBuildOnOpen) {
            showCloudBuildDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = StudioColors.Surface,
                modifier = Modifier.width(300.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = projectDir.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            crudInput = ""
                            fileCrudAction = "new_file" to projectDir
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.NoteAdd, contentDescription = "New File", tint = StudioColors.AccentBlue)
                    }
                    IconButton(
                        onClick = {
                            crudInput = ""
                            fileCrudAction = "new_folder" to projectDir
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New Folder", tint = StudioColors.AccentGreen)
                    }
                    IconButton(
                        onClick = { refreshTree() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Tree", tint = StudioColors.TextSecondary)
                    }
                }
                HorizontalDivider(color = StudioColors.Divider)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(fileNodes, key = { it.file.absolutePath }) { node ->
                        var nodeMenu by remember { mutableStateOf(false) }
                        val isExpanded = expandedDirs.contains(node.file.absolutePath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (node.isDirectory) {
                                        expandedDirs = if (isExpanded) {
                                            expandedDirs - node.file.absolutePath
                                        } else {
                                            expandedDirs + node.file.absolutePath
                                        }
                                        refreshTree()
                                    } else {
                                        openFileInEditor(node.file)
                                        scope.launch { drawerState.close() }
                                    }
                                }
                                .padding(
                                    start = (12 + node.depth * 14).dp,
                                    top = 7.dp,
                                    bottom = 7.dp,
                                    end = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    node.isDirectory && isExpanded -> Icons.Outlined.FolderOpen
                                    node.isDirectory -> Icons.Outlined.Folder
                                    else -> Icons.Outlined.Description
                                },
                                contentDescription = null,
                                tint = if (node.isDirectory) StudioColors.AccentAmber else StudioColors.AccentBlue,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(
                                    onClick = { nodeMenu = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "File Options",
                                        tint = StudioColors.TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = nodeMenu,
                                    onDismissRequest = { nodeMenu = false }
                                ) {
                                    if (node.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text("New File Here") },
                                            onClick = {
                                                nodeMenu = false
                                                crudInput = ""
                                                fileCrudAction = "new_file" to node.file
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New Folder Here") },
                                            onClick = {
                                                nodeMenu = false
                                                crudInput = ""
                                                fileCrudAction = "new_folder" to node.file
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            nodeMenu = false
                                            crudInput = node.name
                                            fileCrudAction = "rename" to node.file
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = StudioColors.AccentRed) },
                                        onClick = {
                                            nodeMenu = false
                                            fileCrudAction = "delete" to node.file
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = StudioColors.Background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Surface),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "File Explorer")
                        }
                    },
                    title = {
                        val activeTab = openTabs.getOrNull(activeTabIndex)
                        Column {
                            Text(
                                text = activeTab?.file?.name ?: projectDir.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = toastStatus ?: projectDir.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.AccentBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { editorController.undo() }) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { editorController.redo() }) {
                            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { saveCurrentFile() }) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save File")
                        }
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Find & Replace")
                        }
                        IconButton(onClick = { showGitDialog = true }) {
                            Icon(StudioIcons.GitBranch, contentDescription = "Git Sync", tint = StudioColors.AccentBlue)
                        }
                        IconButton(onClick = { showCloudBuildDialog = true }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Run Cloud Build", tint = StudioColors.AccentGreen)
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "IDE Menu")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("App Config Editor") },
                                    onClick = { showOverflowMenu = false; showAppConfigDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("APK / AAB Inspector") },
                                    onClick = { showOverflowMenu = false; showApkInspectorDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Generate CI Workflow") },
                                    onClick = {
                                        showOverflowMenu = false
                                        val pkg = workspaceRepository.parsePackageId(projectDir)
                                        val wf = ProjectTemplateGenerator.writeWorkflowFile(projectDir, pkg, projectDir.name)
                                        refreshTree()
                                        openFileInEditor(wf)
                                        toastStatus = "Generated .github/workflows/build.yml"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { showOverflowMenu = false; onOpenSettings() }
                                )
                                HorizontalDivider(color = StudioColors.Divider)
                                DropdownMenuItem(
                                    text = { Text("Back to Dashboard") },
                                    onClick = { showOverflowMenu = false; onBackHome() }
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Multi-Tab Bar
                if (openTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioColors.Surface)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        openTabs.forEachIndexed { index, tab ->
                            val selected = index == activeTabIndex
                            Surface(
                                color = if (selected) StudioColors.Background else StudioColors.Surface,
                                border = BorderStroke(0.5.dp, StudioColors.Divider),
                                modifier = Modifier.clickable { activeTabIndex = index }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (tab.isModified) "• ${tab.file.name}" else tab.file.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) StudioColors.AccentBlue else StudioColors.TextSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Close Tab",
                                        tint = StudioColors.TextSecondary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                openTabs.removeAt(index)
                                                activeTabIndex = when {
                                                    openTabs.isEmpty() -> -1
                                                    activeTabIndex >= openTabs.size -> openTabs.lastIndex
                                                    else -> activeTabIndex
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Inline Search & Replace Bar
                if (showSearchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioColors.SurfaceElevated)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editorController.findNext(searchQuery) }) { Text("Find") }
                        TextButton(onClick = { editorController.replaceCurrent(replaceQuery) }) { Text("Rep") }
                        IconButton(onClick = {
                            editorController.stopSearch()
                            showSearchBar = false
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close Search")
                        }
                    }
                }

                // Center Sora Code Editor
                val activeTab = openTabs.getOrNull(activeTabIndex)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeTab != null) {
                        SoraCodeEditor(
                            filePath = activeTab.file.absolutePath,
                            fileName = activeTab.file.name,
                            text = activeTab.content,
                            controller = editorController,
                            onTextChanged = { updated ->
                                val idx = openTabs.indexOfFirst { it.file.absolutePath == activeTab.file.absolutePath }
                                if (idx >= 0) {
                                    openTabs[idx] = openTabs[idx].copy(
                                        content = updated,
                                        isModified = true
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Open a file from the Explorer Drawer", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("Open Explorer")
                            }
                        }
                    }
                }

                // Quick Symbol Bar
                QuickExtraKeysRow(
                    keys = listOf(
                        "{" to { editorController.insertText("{}") },
                        "}" to { editorController.insertText("}") },
                        "[" to { editorController.insertText("[]") },
                        "]" to { editorController.insertText("]") },
                        "(" to { editorController.insertText("()") },
                        ")" to { editorController.insertText(")") },
                        ";" to { editorController.insertText(";") },
                        "." to { editorController.insertText(".") },
                        "\"" to { editorController.insertText("\"\"") },
                        "=" to { editorController.insertText(" = ") },
                        "tab" to { editorController.insertText("    ") },
                        "agy" to {
                            bottomTab = 0
                            bottomSheetState = if (bottomSheetState == 0) 1 else bottomSheetState
                        },
                        "->" to { editorController.insertText(" -> ") }
                    )
                )

                // Collapsible Bottom Sheet Panel
                val panelHeight = when (bottomSheetState) {
                    0 -> 42.dp
                    1 -> 230.dp
                    else -> 380.dp
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(panelHeight),
                    color = StudioColors.Surface,
                    border = BorderStroke(1.dp, StudioColors.Divider)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TabRow(
                                selectedTabIndex = bottomTab,
                                containerColor = StudioColors.Surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Tab(selected = bottomTab == 0, onClick = {
                                    bottomTab = 0
                                    if (bottomSheetState == 0) bottomSheetState = 1
                                }, text = { Text("Terminal", fontSize = 11.sp, maxLines = 1) })
                                Tab(selected = bottomTab == 1, onClick = {
                                    bottomTab = 1
                                    if (bottomSheetState == 0) bottomSheetState = 1
                                }, text = { Text("Build Logs", fontSize = 11.sp, maxLines = 1) })
                                Tab(selected = bottomTab == 2, onClick = {
                                    bottomTab = 2
                                    if (bottomSheetState == 0) bottomSheetState = 1
                                    scope.launch { logcatLines = LogcatReader.dumpAppLogcat() }
                                }, text = { Text("Logcat", fontSize = 11.sp, maxLines = 1) })
                                Tab(selected = bottomTab == 3, onClick = {
                                    bottomTab = 3
                                    if (bottomSheetState == 0) bottomSheetState = 1
                                }, text = {
                                    Text("Diag (${buildState.diagnostics.size})", fontSize = 11.sp, maxLines = 1)
                                })
                            }
                            IconButton(
                                onClick = { bottomSheetState = (bottomSheetState + 1) % 3 },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (bottomSheetState == 0) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = "Toggle Panel Height",
                                    tint = StudioColors.TextSecondary
                                )
                            }
                        }

                        if (bottomSheetState > 0) {
                            when (bottomTab) {
                                0 -> EmbeddedLinuxTerminalPanel(
                                    linuxEnvironmentManager = linuxEnvironmentManager,
                                    downloadManager = downloadManager,
                                    settingsRepository = settingsRepository,
                                    modifier = Modifier.fillMaxSize()
                                )
                                1 -> BuildLogsTabContent(
                                    buildState = buildState,
                                    onRefresh = { scope.launch { cloudBuildManager.refreshRuns() } },
                                    onDownloadArtifact = { runId, art ->
                                        scope.launch {
                                            cloudBuildManager.downloadAndExtractArtifact(runId, art)
                                                .onSuccess { files -> toastStatus = "Extracted ${files.size} artifact(s)" }
                                                .onFailure { err -> toastStatus = err.message }
                                        }
                                    },
                                    onInstallApk = { apk ->
                                        if (!ApkInstaller.canRequestPackageInstalls(context)) {
                                            ApkInstaller.openInstallPermissionSettings(context)
                                        } else {
                                            ApkInstaller.launchInstaller(context, apk)
                                                .onFailure { toastStatus = it.message }
                                        }
                                    }
                                )
                                2 -> LogcatTabContent(
                                    lines = logcatLines,
                                    onRefresh = { scope.launch { logcatLines = LogcatReader.dumpAppLogcat() } }
                                )
                                3 -> DiagnosticsTabContent(
                                    diagnostics = buildState.diagnostics,
                                    onJump = { diag ->
                                        val relName = File(diag.filePath).name
                                        val localFile = projectDir.walkTopDown().firstOrNull {
                                            it.isFile && (diag.filePath.endsWith(it.name) && it.name == relName)
                                        }
                                        if (localFile != null) {
                                            openFileInEditor(localFile, diag.line, diag.column)
                                        } else {
                                            toastStatus = "File not in local project: $relName"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // File CRUD Dialog
    fileCrudAction?.let { (action, target) ->
        AlertDialog(
            onDismissRequest = { fileCrudAction = null },
            containerColor = StudioColors.Surface,
            title = {
                Text(
                    when (action) {
                        "new_file" -> "Create File in ${target.name}"
                        "new_folder" -> "Create Folder in ${target.name}"
                        "rename" -> "Rename ${target.name}"
                        else -> "Delete ${target.name}?"
                    }
                )
            },
            text = {
                if (action == "delete") {
                    Text("Permanently delete ${target.name}?", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = crudInput,
                        onValueChange = { crudInput = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            "new_file" -> workspaceRepository.createFile(target, crudInput).onSuccess {
                                expandedDirs = expandedDirs + target.absolutePath
                                refreshTree()
                                openFileInEditor(it)
                            }.onFailure { toastStatus = it.message }
                            "new_folder" -> workspaceRepository.createFolder(target, crudInput).onSuccess {
                                expandedDirs = expandedDirs + target.absolutePath + it.absolutePath
                                refreshTree()
                            }.onFailure { toastStatus = it.message }
                            "rename" -> workspaceRepository.renameNode(target, crudInput).onSuccess {
                                refreshTree()
                            }.onFailure { toastStatus = it.message }
                            "delete" -> workspaceRepository.deleteNode(target).onSuccess {
                                openTabs.removeAll { it.file.absolutePath == target.absolutePath }
                                if (activeTabIndex >= openTabs.size) activeTabIndex = openTabs.lastIndex
                                refreshTree()
                            }.onFailure { toastStatus = it.message }
                        }
                        fileCrudAction = null
                    }
                ) {
                    Text(if (action == "delete") "Delete" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileCrudAction = null }) { Text("Cancel") }
            }
        )
    }

    if (showGitDialog) {
        GitSyncDialog(
            projectDir = projectDir,
            gitEngine = gitEngine,
            gitHubRepository = gitHubRepository,
            onDismiss = { showGitDialog = false },
            onStatusChanged = { toastStatus = it }
        )
    }

    if (showAppConfigDialog) {
        AppConfigEditorDialog(
            projectDir = projectDir,
            onDismiss = { showAppConfigDialog = false },
            onSaved = {
                refreshTree()
                toastStatus = "Updated app Gradle config"
            }
        )
    }

    if (showApkInspectorDialog) {
        ApkInspectorDialog(
            initialArchives = buildState.extractedFiles,
            onDismiss = { showApkInspectorDialog = false }
        )
    }

    if (showCloudBuildDialog) {
        CloudBuildTriggerDialog(
            projectDir = projectDir,
            workspaceRepository = workspaceRepository,
            gitHubRepository = gitHubRepository,
            cloudBuildManager = cloudBuildManager,
            onDismiss = { showCloudBuildDialog = false },
            onTriggered = {
                showCloudBuildDialog = false
                bottomTab = 1
                if (bottomSheetState == 0) bottomSheetState = 1
            }
        )
    }
}

@Composable
private fun BuildLogsTabContent(
    buildState: com.clouddroidide.android.build.CloudBuildState,
    onRefresh: () -> Unit,
    onDownloadArtifact: (Long, com.clouddroidide.android.data.remote.model.WorkflowArtifact) -> Unit,
    onInstallApk: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val run = buildState.selectedRun
            Text(
                text = if (run != null) "Run #${run.runNumber} • ${run.status} (${run.conclusion ?: "running"})"
                else "No workflow run loaded",
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.AccentBlue
            )
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (buildState.isPolling) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Polling GitHub Actions...", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!buildState.errorMessage.isNullOrBlank()) {
            Text(buildState.errorMessage, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
        }
        buildState.jobs.forEach { job ->
            Text("${job.name}: ${job.conclusion ?: job.status}", style = MaterialTheme.typography.labelSmall)
            job.steps.forEach { step ->
                Text(
                    "  [${step.conclusion ?: step.status}] ${step.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (step.conclusion) {
                        "success" -> StudioColors.AccentGreen
                        "failure" -> StudioColors.AccentRed
                        else -> StudioColors.TextSecondary
                    }
                )
            }
        }
        if (buildState.artifacts.isNotEmpty() && buildState.selectedRun != null) {
            Text("Build Artifacts", style = MaterialTheme.typography.labelMedium)
            buildState.artifacts.forEach { art ->
                OutlinedButton(onClick = { onDownloadArtifact(buildState.selectedRun.id, art) }) {
                    Text("Download ${art.name} (${art.sizeInBytes / 1024} KB)")
                }
            }
        }
        buildState.extractedFiles.filter { it.extension.equals("apk", true) }.forEach { apk ->
            Button(
                onClick = { onInstallApk(apk) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGreen)
            ) {
                Text("One-Tap Install ${apk.name}")
            }
        }
        if (buildState.logsText.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = buildState.logsText.takeLast(8000),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = StudioColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun LogcatTabContent(lines: List<String>, onRefresh: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Device Logcat (${lines.size} lines)", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = lines.joinToString("\n").ifBlank { "No logcat entries captured yet." },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = StudioColors.TextPrimary
            )
        }
    }
}

@Composable
private fun DiagnosticsTabContent(
    diagnostics: List<com.clouddroidide.android.workspace.BuildDiagnostic>,
    onJump: (com.clouddroidide.android.workspace.BuildDiagnostic) -> Unit
) {
    if (diagnostics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No compiler errors or warnings in current build logs", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(diagnostics) { diag ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StudioColors.SurfaceElevated,
                    border = BorderStroke(1.dp, StudioColors.Divider),
                    modifier = Modifier.fillMaxWidth().clickable { onJump(diag) }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "${File(diag.filePath).name}:${diag.line}:${diag.column}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diag.severity == DiagnosticSeverity.ERROR) StudioColors.AccentRed else StudioColors.AccentAmber
                        )
                        Text(diag.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GitSyncDialog(
    projectDir: File,
    gitEngine: GitEngine,
    gitHubRepository: GitHubRepository,
    onDismiss: () -> Unit,
    onStatusChanged: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth by gitHubRepository.authState.collectAsState()
    var status by remember { mutableStateOf<GitRepoStatus?>(null) }
    var remoteUrl by remember { mutableStateOf("") }
    var commitMsg by remember { mutableStateOf("") }
    var branchInput by remember { mutableStateOf("") }
    var outputMsg by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            isBusy = true
            gitEngine.getStatus(projectDir)
                .onSuccess {
                    status = it
                    if (remoteUrl.isBlank()) remoteUrl = it.remoteUrl
                }
                .onFailure { outputMsg = it.message }
            isBusy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("Git Sync — ${projectDir.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Branch: ${status?.branch ?: "..."} • Changes: ${status?.changedFiles?.size ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.AccentBlue
                )
                if (!File(projectDir, ".git").exists()) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("GitHub Remote URL (https://github.com/user/repo.git)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                gitEngine.initAndSetRemote(
                                    projectDir,
                                    remoteUrl.trim(),
                                    auth.username ?: "CloudDroid",
                                    "${auth.username ?: "dev"}@users.noreply.github.com"
                                ).onSuccess {
                                    outputMsg = it
                                    reload()
                                }.onFailure { outputMsg = it.message }
                                isBusy = false
                            }
                        }
                    ) { Text("Initialize Git & Set Origin") }
                } else {
                    status?.changedFiles?.take(8)?.forEach { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedTextField(
                        value = commitMsg,
                        onValueChange = { commitMsg = it },
                        label = { Text("Commit Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isBusy && commitMsg.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    gitEngine.stageAllAndCommit(projectDir, commitMsg, auth.username ?: "CloudDroid")
                                        .onSuccess {
                                            commitMsg = ""
                                            outputMsg = it
                                            onStatusChanged("Committed changes")
                                            reload()
                                        }
                                        .onFailure { outputMsg = it.message }
                                    isBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Commit") }
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    gitEngine.pull(projectDir)
                                        .onSuccess { outputMsg = it; reload() }
                                        .onFailure { outputMsg = it.message }
                                    isBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Pull") }
                    }
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                gitEngine.push(projectDir, status?.branch ?: "main")
                                    .onSuccess {
                                        outputMsg = it
                                        onStatusChanged("Pushed to GitHub")
                                    }
                                    .onFailure { outputMsg = it.message }
                                isBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Push to Origin") }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Switch / New Branch") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            enabled = !isBusy && branchInput.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    val exists = status?.branches?.contains(branchInput.trim()) == true
                                    gitEngine.checkoutBranch(projectDir, branchInput, create = !exists)
                                        .onSuccess { outputMsg = it; reload() }
                                        .onFailure { outputMsg = it.message }
                                    isBusy = false
                                }
                            }
                        ) { Text("Go") }
                    }
                }
                if (!outputMsg.isNullOrBlank()) {
                    Text(outputMsg!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentAmber)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun AppConfigEditorDialog(
    projectDir: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val initial = remember(projectDir) { AppConfigEditor.readConfig(projectDir) }
    var config by remember { mutableStateOf(initial.getOrNull()) }
    var appId by remember { mutableStateOf(config?.applicationId ?: "") }
    var versionCode by remember { mutableStateOf(config?.versionCode?.toString() ?: "1") }
    var versionName by remember { mutableStateOf(config?.versionName ?: "1.0.0") }
    var minSdk by remember { mutableStateOf(config?.minSdk?.toString() ?: "26") }
    var targetSdk by remember { mutableStateOf(config?.targetSdk?.toString() ?: "36") }
    var errorMsg by remember { mutableStateOf(initial.exceptionOrNull()?.message) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("App Config Editor") },
        text = {
            if (config == null) {
                Text(errorMsg ?: "app/build.gradle(.kts) not found", color = StudioColors.AccentRed)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("applicationId") }, singleLine = true)
                    OutlinedTextField(value = versionCode, onValueChange = { versionCode = it }, label = { Text("versionCode") }, singleLine = true)
                    OutlinedTextField(value = versionName, onValueChange = { versionName = it }, label = { Text("versionName") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = minSdk, onValueChange = { minSdk = it }, label = { Text("minSdk") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = targetSdk, onValueChange = { targetSdk = it }, label = { Text("targetSdk") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (!errorMsg.isNullOrBlank()) {
                        Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
                    }
                }
            }
        },
        confirmButton = {
            if (config != null) {
                Button(
                    onClick = {
                        val updated = AppGradleConfig(
                            gradleFile = config!!.gradleFile,
                            applicationId = appId.trim(),
                            versionCode = versionCode.toIntOrNull() ?: 0,
                            versionName = versionName.trim(),
                            minSdk = minSdk.toIntOrNull() ?: 0,
                            targetSdk = targetSdk.toIntOrNull() ?: 0
                        )
                        AppConfigEditor.writeConfig(updated)
                            .onSuccess { onSaved(); onDismiss() }
                            .onFailure { errorMsg = it.message }
                    }
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApkInspectorDialog(
    initialArchives: List<File>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inspected by remember { mutableStateOf<InspectedArchive?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                }
                ApkInspector.inspect(context, tempFile)
                    .onSuccess { inspected = it; errorMsg = null }
                    .onFailure { errorMsg = it.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("APK / AAB Inspector") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick .apk or .aab File")
                }
                initialArchives.forEach { file ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ApkInspector.inspect(context, file)
                                    .onSuccess { inspected = it; errorMsg = null }
                                    .onFailure { errorMsg = it.message }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inspect ${file.name}")
                    }
                }
                inspected?.let { info ->
                    HorizontalDivider(color = StudioColors.Divider)
                    Text("Type: ${info.fileType}", style = MaterialTheme.typography.titleSmall)
                    Text("Package: ${info.packageName}", style = MaterialTheme.typography.bodySmall)
                    Text("Version: ${info.versionName} (${info.versionCode})", style = MaterialTheme.typography.bodySmall)
                    Text("Size: %.2f MB".format(info.sizeBytes / (1024f * 1024f)), style = MaterialTheme.typography.bodySmall)
                    Text("Zip Entries: ${info.entriesCount}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256: ${info.sha256Fingerprint}", style = MaterialTheme.typography.labelSmall)
                    if (info.permissions.isNotEmpty()) {
                        Text("Permissions (${info.permissions.size}):", style = MaterialTheme.typography.labelMedium)
                        Text(info.permissions.joinToString("\n"), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CloudBuildTriggerDialog(
    projectDir: File,
    workspaceRepository: ProjectWorkspaceRepository,
    gitHubRepository: GitHubRepository,
    cloudBuildManager: CloudBuildManager,
    onDismiss: () -> Unit,
    onTriggered: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val detected = remember(projectDir) { workspaceRepository.parseGitRemoteOwnerRepo(projectDir) }
    var repoFullName by remember {
        mutableStateOf(detected?.let { "${it.first}/${it.second}" } ?: "")
    }
    var branch by remember {
        mutableStateOf(workspaceRepository.parseGitBranch(projectDir).takeIf { it != "no-git" } ?: "main")
    }
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        gitHubRepository.listRepos().onSuccess { list ->
            repos = list.map { it.fullName }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("Run Cloud Build (GitHub Actions)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = repoFullName,
                    onValueChange = { repoFullName = it },
                    label = { Text("GitHub Repository (owner/repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (repos.isNotEmpty()) {
                    repos.take(4).forEach { r ->
                        Text(
                            text = r,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.AccentBlue,
                            modifier = Modifier.clickable { repoFullName = r }.padding(vertical = 2.dp)
                        )
                    }
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = repoFullName.contains("/"),
                onClick = {
                    scope.launch {
                        cloudBuildManager.triggerAndTrack(repoFullName, branch)
                            .onSuccess { onTriggered() }
                            .onFailure { errorMsg = it.message }
                    }
                }
            ) {
                Text("Trigger Build")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
