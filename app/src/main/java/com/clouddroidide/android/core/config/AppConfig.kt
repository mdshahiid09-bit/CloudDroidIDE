package com.clouddroidide.android.core.config

import com.clouddroidide.android.BuildConfig

/**
 * Centralized zero-backend configuration for CloudDroid IDE.
 * All values are cleanly overridable at runtime via DataStore-backed Settings.
 */
object AppConfig {
    const val DEFAULT_UPDATE_REPO_OWNER = "mdshahiid09-bit"
    const val DEFAULT_UPDATE_REPO_NAME = "CloudDroidIDE"

    const val ALPINE_VERSION = "3.24.4"
    const val DEFAULT_ROOTFS_URL =
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/alpine-minirootfs-$ALPINE_VERSION-aarch64.tar.gz"
    const val DEFAULT_PROOT_URL =
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/community/aarch64/proot-static-5.4.0-r2.apk"

    /**
     * Configurable Antigravity CLI (`agy`) install source.
     * Kept blank until configured by the user in Environment Setup or Settings
     * so no placeholder or dummy URL is ever shipped.
     */
    const val DEFAULT_AGY_INSTALL_SOURCE = ""

    /**
     * Optional GitHub OAuth App Client ID supplied at build time via local.properties/gradle.properties
     * or at runtime in Settings for GitHub Device Flow OAuth.
     */
    val defaultOAuthClientId: String
        get() = BuildConfig.GITHUB_OAUTH_CLIENT_ID

    const val GITHUB_API_BASE_URL = "https://api.github.com/"
    const val GITHUB_WEB_BASE_URL = "https://github.com/"

    const val PROJECTS_DIR_NAME = "projects"
    const val LINUX_ENV_DIR_NAME = "linux_env"
    const val KEYSTORES_DIR_NAME = "keystores"
    const val DOWNLOADS_DIR_NAME = "downloads"
    const val ARTIFACTS_DIR_NAME = "artifacts"
    const val UPDATES_DIR_NAME = "updates"
}
