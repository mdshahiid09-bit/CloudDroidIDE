package com.clouddroidide.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clouddroidide.android.core.config.IdeSettings
import com.clouddroidide.android.core.theme.CloudDroidTheme
import com.clouddroidide.android.core.theme.StudioColors
import com.clouddroidide.android.ui.components.ForcedUpdateDialog
import com.clouddroidide.android.ui.components.GitHubAuthDialog
import com.clouddroidide.android.ui.screens.BuildHistoryScreen
import com.clouddroidide.android.ui.screens.HomeScreen
import com.clouddroidide.android.ui.screens.IdeWorkspaceScreen
import com.clouddroidide.android.ui.screens.KeystoreSecretsScreen
import com.clouddroidide.android.ui.screens.PlayStoreScreen
import com.clouddroidide.android.ui.screens.SettingsScreen
import com.clouddroidide.android.ui.screens.TerminalScreen
import java.io.File

private sealed class NavRoute(val route: String, val label: String) {
    data object Workspace : NavRoute("workspace", "Workspace")
    data object Keystores : NavRoute("keystores", "Keystores & Secrets")
    data object Builds : NavRoute("builds", "Build History")
    data object PlayStore : NavRoute("playstore", "Play Store")
    data object Ide : NavRoute("ide", "IDE")
    data object Terminal : NavRoute("terminal", "Terminal")
    data object Settings : NavRoute("settings", "Settings")
}

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            CloudDroidTheme {
                CloudDroidRootScreen()
            }
        }
    }
}

@Composable
private fun CloudDroidRootScreen() {
    val container = CloudDroidApp.container
    val navController = rememberNavController()

    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = IdeSettings())
    val mandatoryUpdate by container.updateManager.mandatoryUpdate.collectAsState()
    val updateTaskId by container.updateManager.activeUpdateTaskId.collectAsState()
    val downloadTasks by container.downloadManager.tasks.collectAsState()

    var showAuthDialog by remember { mutableStateOf(false) }
    var activeProjectDir by remember { mutableStateOf<File?>(null) }
    var autoTriggerCloudBuild by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.gitHubRepository.refreshUserIfLoggedIn()
        container.updateManager.checkForUpdates()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(
        NavRoute.Workspace.route,
        NavRoute.Keystores.route,
        NavRoute.Builds.route,
        NavRoute.PlayStore.route
    )

    Scaffold(
        containerColor = StudioColors.Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = StudioColors.Surface) {
                    val items = listOf(
                        Triple(NavRoute.Workspace, Icons.Outlined.Code, "Workspace"),
                        Triple(NavRoute.Keystores, Icons.Outlined.Key, "Keystores"),
                        Triple(NavRoute.Builds, Icons.Outlined.Build, "Builds"),
                        Triple(NavRoute.PlayStore, Icons.Outlined.Publish, "Play Store")
                    )
                    items.forEach { (dest, icon, shortLabel) ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = dest.label) },
                            label = { Text(shortLabel, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = NavRoute.Workspace.route
            ) {
                composable(NavRoute.Workspace.route) {
                    HomeScreen(
                        workspaceRepository = container.workspaceRepository,
                        gitHubRepository = container.gitHubRepository,
                        linuxEnvironmentManager = container.linuxEnvironmentManager,
                        downloadManager = container.downloadManager,
                        gitEngine = container.gitEngine,
                        onOpenProject = { dir, cloudBuild ->
                            activeProjectDir = dir
                            autoTriggerCloudBuild = cloudBuild
                            navController.navigate(NavRoute.Ide.route)
                        },
                        onOpenTerminal = { navController.navigate(NavRoute.Terminal.route) },
                        onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                        onOpenAuthDialog = { showAuthDialog = true }
                    )
                }

                composable(NavRoute.Keystores.route) {
                    KeystoreSecretsScreen(
                        keystoresDir = container.keystoresDir,
                        secureStorage = container.secureStorage,
                        gitHubRepository = container.gitHubRepository,
                        settingsRepository = container.settingsRepository
                    )
                }

                composable(NavRoute.Builds.route) {
                    BuildHistoryScreen(
                        cloudBuildManager = container.cloudBuildManager,
                        gitHubRepository = container.gitHubRepository,
                        settingsRepository = container.settingsRepository,
                        downloadManager = container.downloadManager
                    )
                }

                composable(NavRoute.PlayStore.route) {
                    PlayStoreScreen(
                        secureStorage = container.secureStorage,
                        gitHubRepository = container.gitHubRepository,
                        cloudBuildManager = container.cloudBuildManager,
                        settingsRepository = container.settingsRepository
                    )
                }

                composable(NavRoute.Ide.route) {
                    val dir = activeProjectDir
                    if (dir != null && dir.exists()) {
                        IdeWorkspaceScreen(
                            projectDir = dir,
                            triggerCloudBuildOnOpen = autoTriggerCloudBuild,
                            workspaceRepository = container.workspaceRepository,
                            linuxEnvironmentManager = container.linuxEnvironmentManager,
                            downloadManager = container.downloadManager,
                            settingsRepository = container.settingsRepository,
                            gitHubRepository = container.gitHubRepository,
                            gitEngine = container.gitEngine,
                            cloudBuildManager = container.cloudBuildManager,
                            onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                            onBackHome = { navController.popBackStack() }
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable(NavRoute.Terminal.route) {
                    TerminalScreen(
                        linuxEnvironmentManager = container.linuxEnvironmentManager,
                        downloadManager = container.downloadManager,
                        settingsRepository = container.settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoute.Settings.route) {
                    SettingsScreen(
                        settingsRepository = container.settingsRepository,
                        gitHubRepository = container.gitHubRepository,
                        updateManager = container.updateManager,
                        onOpenAuthDialog = { showAuthDialog = true },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    if (showAuthDialog) {
        GitHubAuthDialog(
            gitHubRepository = container.gitHubRepository,
            settings = settings,
            onDismiss = { showAuthDialog = false },
            onOpenSettings = { navController.navigate(NavRoute.Settings.route) }
        )
    }

    mandatoryUpdate?.let { update ->
        ForcedUpdateDialog(
            update = update,
            activeTask = updateTaskId?.let { downloadTasks[it] },
            downloadManager = container.downloadManager,
            updateManager = container.updateManager
        )
    }
}
