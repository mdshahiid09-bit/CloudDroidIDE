package com.clouddroidide.android.ui.screens

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.core.ui.StudioIcons
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.data.remote.model.GitHubRepo
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadStatus
import com.clouddroidide.android.terminal.LinuxEnvPhase
import com.clouddroidide.android.terminal.LinuxEnvironmentManager
import com.clouddroidide.android.ui.components.DownloadTaskCard
import com.clouddroidide.android.workspace.AndroidProject
import com.clouddroidide.android.workspace.GitEngine
import com.clouddroidide.android.workspace.ProjectTemplateGenerator
import com.clouddroidide.android.workspace.ProjectTemplateType
import com.clouddroidide.android.workspace.ProjectWorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspaceRepository: ProjectWorkspaceRepository,
    gitHubRepository: GitHubRepository,
    linuxEnvironmentManager: LinuxEnvironmentManager,
    downloadManager: DownloadManager,
    gitEngine: GitEngine,
    onOpenProject: (File, Boolean) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuthDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by gitHubRepository.authState.collectAsState()
    val envState by linuxEnvironmentManager.state.collectAsState()
    val downloadTasks by downloadManager.tasks.collectAsState()

    var projects by remember { mutableStateOf(workspaceRepository.listProjects()) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showOpenProjectDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<AndroidProject?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    fun refreshProjects() {
        projects = workspaceRepository.listProjects()
    }

    LaunchedEffect(Unit) {
        refreshProjects()
        linuxEnvironmentManager.refreshState()
    }

    val safDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
                    val targetName = (doc.name ?: "ImportedProject").replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val targetDir = File(workspaceRepository.projectsRoot, targetName)
                    if (!targetDir.exists()) targetDir.mkdirs()
                    fun copyDocTree(src: DocumentFile, destDir: File) {
                        src.listFiles().forEach { child ->
                            val name = child.name ?: return@forEach
                            if (name == ".gradle" || name == "build") return@forEach
                            if (child.isDirectory) {
                                val sub = File(destDir, name).apply { mkdirs() }
                                copyDocTree(child, sub)
                            } else if (child.isFile) {
                                context.contentResolver.openInputStream(child.uri)?.use { input ->
                                    File(destDir, name).outputStream().use { out -> input.copyTo(out) }
                                }
                            }
                        }
                    }
                    copyDocTree(doc, targetDir)
                    targetDir
                }
                if (imported != null) {
                    refreshProjects()
                    onOpenProject(imported, false)
                }
            }
        }
    }

    val activeDownload = downloadTasks.values.lastOrNull {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    Scaffold(
        containerColor = StudioColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioColors.Background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = StudioIcons.CloudDroidLogo,
                            contentDescription = "CloudDroid IDE",
                            tint = StudioColors.AccentBlue,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("CloudDroid IDE", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAuthDialog) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = "GitHub Account",
                            tint = if (authState.isAuthenticated) StudioColors.AccentGreen else StudioColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = StudioColors.TextSecondary
                        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeDownload != null) {
                item {
                    DownloadTaskCard(task = activeDownload, downloadManager = downloadManager)
                }
            } else if (envState.phase != LinuxEnvPhase.READY) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Surface,
                        border = BorderStroke(1.dp, StudioColors.Divider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTerminal)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = StudioColors.AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AArch64 Linux Environment Setup Required",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tap to bootstrap Alpine rootfs, PRoot, git, and agy",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (!bannerMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioColors.SurfaceElevated,
                        border = BorderStroke(1.dp, StudioColors.Divider)
                    ) {
                        Text(
                            text = bannerMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.AccentBlue,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionGridCard(
                            title = "New Project",
                            subtitle = "Compose / XML",
                            icon = Icons.Outlined.Add,
                            accent = StudioColors.AccentBlue,
                            onClick = { showNewProjectDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        ActionGridCard(
                            title = "Open Project",
                            subtitle = "Workspace / SAF",
                            icon = Icons.Outlined.FolderOpen,
                            accent = StudioColors.AccentGreen,
                            onClick = { showOpenProjectDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionGridCard(
                            title = "Clone Repository",
                            subtitle = "GitHub / HTTPS",
                            icon = Icons.Outlined.CloudDownload,
                            accent = StudioColors.AccentAmber,
                            onClick = { showCloneDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        ActionGridCard(
                            title = "AArch64 Terminal",
                            subtitle = "PRoot + agy CLI",
                            icon = Icons.Outlined.Terminal,
                            accent = StudioColors.TextPrimary,
                            onClick = onOpenTerminal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT PROJECTS (${projects.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioColors.TextSecondary
                    )
                    if (authState.isAuthenticated && authState.username != null) {
                        Text(
                            text = "@${authState.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.AccentGreen
                        )
                    }
                }
            }

            if (projects.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Surface,
                        border = BorderStroke(1.dp, StudioColors.Divider)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No projects in workspace yet", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Create a new Android project or clone a GitHub repository to begin.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.directory.absolutePath }) { project ->
                    ProjectItemRow(
                        project = project,
                        onOpenIde = { onOpenProject(project.directory, false) },
                        onCloudBuild = { onOpenProject(project.directory, true) },
                        onGitSync = { onOpenProject(project.directory, false) },
                        onDelete = { projectToDelete = project }
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectWizardDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, pkg, template ->
                ProjectTemplateGenerator.generateProject(
                    projectsRoot = workspaceRepository.projectsRoot,
                    projectName = name,
                    packageName = pkg,
                    templateType = template
                ).onSuccess { dir ->
                    showNewProjectDialog = false
                    refreshProjects()
                    onOpenProject(dir, false)
                }.onFailure { err ->
                    bannerMessage = err.message
                }
            }
        )
    }

    if (showOpenProjectDialog) {
        AlertDialog(
            onDismissRequest = { showOpenProjectDialog = false },
            containerColor = StudioColors.Surface,
            title = { Text("Open Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Workspace directory: ${workspaceRepository.projectsRoot.absolutePath}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (projects.isNotEmpty()) {
                        projects.take(6).forEach { proj ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioColors.SurfaceElevated,
                                border = BorderStroke(1.dp, StudioColors.Divider),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOpenProjectDialog = false
                                        onOpenProject(proj.directory, false)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(proj.name, style = MaterialTheme.typography.titleSmall)
                                    Text(proj.packageId, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showOpenProjectDialog = false
                            safDirLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Folder via System Picker (SAF)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenProjectDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCloneDialog) {
        CloneRepositoryDialog(
            gitHubRepository = gitHubRepository,
            gitEngine = gitEngine,
            isAuthenticated = authState.isAuthenticated,
            onDismiss = { showCloneDialog = false },
            onCloned = { dir ->
                showCloneDialog = false
                refreshProjects()
                onOpenProject(dir, false)
            }
        )
    }

    if (projectToDelete != null) {
        val target = projectToDelete!!
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = StudioColors.Surface,
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    "This permanently removes ${target.directory.absolutePath} from local storage.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceRepository.deleteNode(target.directory)
                        projectToDelete = null
                        refreshProjects()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.Surface,
        border = BorderStroke(1.dp, StudioColors.Divider)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectItemRow(
    project: AndroidProject,
    onOpenIde: () -> Unit,
    onCloudBuild: () -> Unit,
    onGitSync: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeAgo = remember(project.lastModifiedMillis) {
        DateUtils.getRelativeTimeSpanString(
            project.lastModifiedMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenIde),
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.Surface,
        border = BorderStroke(1.dp, StudioColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = project.packageId,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = StudioIcons.GitBranch,
                        contentDescription = null,
                        tint = StudioColors.AccentBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = project.gitBranch,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.AccentBlue
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Project Actions",
                        tint = StudioColors.TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open IDE") },
                        onClick = { menuExpanded = false; onOpenIde() }
                    )
                    DropdownMenuItem(
                        text = { Text("Cloud Build") },
                        onClick = { menuExpanded = false; onCloudBuild() }
                    )
                    DropdownMenuItem(
                        text = { Text("Git Sync") },
                        onClick = { menuExpanded = false; onGitSync() }
                    )
                    HorizontalDivider(color = StudioColors.Divider)
                    DropdownMenuItem(
                        text = { Text("Delete", color = StudioColors.AccentRed) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectWizardDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, ProjectTemplateType) -> Unit
) {
    var name by remember { mutableStateOf("MyAndroidApp") }
    var packageName by remember { mutableStateOf("com.example.myandroidapp") }
    var template by remember { mutableStateOf(ProjectTemplateType.KOTLIN_COMPOSE) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("New Android Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        val slug = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (slug.isNotBlank()) packageName = "com.example.$slug"
                    },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name (applicationId)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Template", style = MaterialTheme.typography.labelMedium)
                ProjectTemplateType.entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { template = item }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = template == item, onClick = { template = item })
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!errorText.isNullOrBlank()) {
                    Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || !packageName.contains('.')) {
                        errorText = "Enter a valid project name and package ID"
                    } else {
                        onCreate(name, packageName, template)
                    }
                }
            ) {
                Text("Create Project")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloneRepositoryDialog(
    gitHubRepository: GitHubRepository,
    gitEngine: GitEngine,
    isAuthenticated: Boolean,
    onDismiss: () -> Unit,
    onCloned: (File) -> Unit
) {
    val scope = rememberCoroutineScope()
    var repoUrl by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var isCloning by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            gitHubRepository.listRepos().onSuccess { repos = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        title = { Text("Clone GitHub Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("HTTPS Repository URL") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (repos.isNotEmpty()) {
                    Text("Your GitHub Repositories", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repos.take(5).forEach { repo ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StudioColors.SurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { repoUrl = repo.cloneUrl }
                            ) {
                                Text(
                                    text = repo.fullName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioColors.AccentBlue,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
                if (isCloning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cloning inside AArch64 container...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = StudioColors.AccentRed)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isCloning && repoUrl.isNotBlank(),
                onClick = {
                    scope.launch {
                        isCloning = true
                        errorMsg = null
                        gitEngine.cloneRepository(repoUrl)
                            .onSuccess { dir ->
                                isCloning = false
                                onCloned(dir)
                            }
                            .onFailure { err ->
                                isCloning = false
                                errorMsg = err.message
                            }
                    }
                }
            ) {
                Text("Clone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
