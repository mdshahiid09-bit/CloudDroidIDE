package com.clouddroidide.android.core.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "clouddroid_settings")

data class IdeSettings(
    val updateRepoOwner: String = AppConfig.DEFAULT_UPDATE_REPO_OWNER,
    val updateRepoName: String = AppConfig.DEFAULT_UPDATE_REPO_NAME,
    val rootfsUrl: String = AppConfig.DEFAULT_ROOTFS_URL,
    val prootUrl: String = AppConfig.DEFAULT_PROOT_URL,
    val agyInstallSource: String = AppConfig.DEFAULT_AGY_INSTALL_SOURCE,
    val oauthClientId: String = AppConfig.defaultOAuthClientId,
    val selectedBuildRepo: String = ""
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val UPDATE_OWNER = stringPreferencesKey("update_repo_owner")
        val UPDATE_NAME = stringPreferencesKey("update_repo_name")
        val ROOTFS_URL = stringPreferencesKey("rootfs_url")
        val PROOT_URL = stringPreferencesKey("proot_url")
        val AGY_SOURCE = stringPreferencesKey("agy_install_source")
        val OAUTH_CLIENT_ID = stringPreferencesKey("oauth_client_id")
        val SELECTED_BUILD_REPO = stringPreferencesKey("selected_build_repo")
    }

    val settingsFlow: Flow<IdeSettings> = context.settingsDataStore.data.map { prefs ->
        IdeSettings(
            updateRepoOwner = prefs[Keys.UPDATE_OWNER]?.takeIf { it.isNotBlank() }
                ?: AppConfig.DEFAULT_UPDATE_REPO_OWNER,
            updateRepoName = prefs[Keys.UPDATE_NAME]?.takeIf { it.isNotBlank() }
                ?: AppConfig.DEFAULT_UPDATE_REPO_NAME,
            rootfsUrl = prefs[Keys.ROOTFS_URL]?.takeIf { it.isNotBlank() }
                ?: AppConfig.DEFAULT_ROOTFS_URL,
            prootUrl = prefs[Keys.PROOT_URL]?.takeIf { it.isNotBlank() }
                ?: AppConfig.DEFAULT_PROOT_URL,
            agyInstallSource = prefs[Keys.AGY_SOURCE] ?: AppConfig.DEFAULT_AGY_INSTALL_SOURCE,
            oauthClientId = prefs[Keys.OAUTH_CLIENT_ID]?.takeIf { it.isNotBlank() }
                ?: AppConfig.defaultOAuthClientId,
            selectedBuildRepo = prefs[Keys.SELECTED_BUILD_REPO] ?: ""
        )
    }

    suspend fun current(): IdeSettings = settingsFlow.first()

    suspend fun updateSettings(transform: (IdeSettings) -> IdeSettings) {
        val current = current()
        val updated = transform(current)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.UPDATE_OWNER] = updated.updateRepoOwner.trim()
            prefs[Keys.UPDATE_NAME] = updated.updateRepoName.trim()
            prefs[Keys.ROOTFS_URL] = updated.rootfsUrl.trim()
            prefs[Keys.PROOT_URL] = updated.prootUrl.trim()
            prefs[Keys.AGY_SOURCE] = updated.agyInstallSource.trim()
            prefs[Keys.OAUTH_CLIENT_ID] = updated.oauthClientId.trim()
            prefs[Keys.SELECTED_BUILD_REPO] = updated.selectedBuildRepo.trim()
        }
    }
}
