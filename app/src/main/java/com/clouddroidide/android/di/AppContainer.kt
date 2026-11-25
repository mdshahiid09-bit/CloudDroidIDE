package com.clouddroidide.android.di

import android.content.Context
import com.clouddroidide.android.build.CloudBuildManager
import com.clouddroidide.android.core.config.AppConfig
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.core.security.SecureStorage
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.terminal.LinuxEnvironmentManager
import com.clouddroidide.android.updater.ForcedUpdateManager
import com.clouddroidide.android.workspace.GitEngine
import com.clouddroidide.android.workspace.ProjectWorkspaceRepository
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val secureStorage = SecureStorage(appContext)
    val gitHubRepository = GitHubRepository(secureStorage)
    val downloadManager = DownloadManager(appContext, secureStorage)
    val updateManager = ForcedUpdateManager(
        context = appContext,
        settingsRepository = settingsRepository,
        gitHubRepository = gitHubRepository,
        downloadManager = downloadManager
    )
    val linuxEnvironmentManager = LinuxEnvironmentManager(
        context = appContext,
        settingsRepository = settingsRepository,
        downloadManager = downloadManager
    )
    val workspaceRepository = ProjectWorkspaceRepository(
        projectsRoot = File(appContext.filesDir, AppConfig.PROJECTS_DIR_NAME)
    )
    val gitEngine = GitEngine(
        envManager = linuxEnvironmentManager,
        secureStorage = secureStorage
    )
    val cloudBuildManager = CloudBuildManager(
        context = appContext,
        gitHubRepository = gitHubRepository,
        downloadManager = downloadManager
    )
    val keystoresDir = File(appContext.filesDir, AppConfig.KEYSTORES_DIR_NAME).apply { mkdirs() }
}
