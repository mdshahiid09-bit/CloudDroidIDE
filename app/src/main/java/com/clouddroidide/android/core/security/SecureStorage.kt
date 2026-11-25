package com.clouddroidide.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores GitHub tokens, keystore passwords, and sensitive credentials using Android Keystore AES-256 encryption.
 */
class SecureStorage(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "clouddroid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGitHubToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setGitHubToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_GITHUB_TOKEN)
            else putString(KEY_GITHUB_TOKEN, token.trim())
        }.apply()
    }

    fun getGitHubUsername(): String? = prefs.getString(KEY_GITHUB_USER, null)?.takeIf { it.isNotBlank() }

    fun setGitHubUsername(username: String?) {
        prefs.edit().apply {
            if (username.isNullOrBlank()) remove(KEY_GITHUB_USER)
            else putString(KEY_GITHUB_USER, username.trim())
        }.apply()
    }

    fun getGitHubAvatarUrl(): String? = prefs.getString(KEY_GITHUB_AVATAR, null)?.takeIf { it.isNotBlank() }

    fun setGitHubAvatarUrl(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_GITHUB_AVATAR)
            else putString(KEY_GITHUB_AVATAR, url.trim())
        }.apply()
    }

    fun saveKeystoreMeta(alias: String, keystorePassword: String, keyPassword: String, fileName: String) {
        prefs.edit()
            .putString("ks_alias_$fileName", alias)
            .putString("ks_store_pass_$fileName", keystorePassword)
            .putString("ks_key_pass_$fileName", keyPassword)
            .apply()
    }

    fun getKeystoreMeta(fileName: String): Triple<String, String, String>? {
        val alias = prefs.getString("ks_alias_$fileName", null) ?: return null
        val storePass = prefs.getString("ks_store_pass_$fileName", null) ?: return null
        val keyPass = prefs.getString("ks_key_pass_$fileName", null) ?: return null
        return Triple(alias, storePass, keyPass)
    }

    fun removeKeystoreMeta(fileName: String) {
        prefs.edit()
            .remove("ks_alias_$fileName")
            .remove("ks_store_pass_$fileName")
            .remove("ks_key_pass_$fileName")
            .apply()
    }

    fun getPlayServiceAccountJson(): String? =
        prefs.getString(KEY_PLAY_SA_JSON, null)?.takeIf { it.isNotBlank() }

    fun setPlayServiceAccountJson(json: String?) {
        prefs.edit().apply {
            if (json.isNullOrBlank()) remove(KEY_PLAY_SA_JSON)
            else putString(KEY_PLAY_SA_JSON, json)
        }.apply()
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_GITHUB_USER)
            .remove(KEY_GITHUB_AVATAR)
            .apply()
    }

    private companion object {
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_GITHUB_USER = "github_username"
        const val KEY_GITHUB_AVATAR = "github_avatar"
        const val KEY_PLAY_SA_JSON = "play_service_account_json"
    }
}
