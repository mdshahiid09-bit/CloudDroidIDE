package com.clouddroidide.android.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val name: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val description: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String = "Created with CloudDroid IDE",
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = false
)

@Serializable
data class GitHubRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null
)

@Serializable
data class RepoPublicKey(
    @SerialName("key_id") val keyId: String,
    val key: String
)

@Serializable
data class PutSecretRequest(
    @SerialName("encrypted_value") val encryptedValue: String,
    @SerialName("key_id") val keyId: String
)

@Serializable
data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class WorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<WorkflowJob> = emptyList()
)

@Serializable
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    val steps: List<WorkflowStep> = emptyList()
)

@Serializable
data class WorkflowStep(
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    val number: Int = 0
)

@Serializable
data class ArtifactsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val artifacts: List<WorkflowArtifact> = emptyList()
)

@Serializable
data class WorkflowArtifact(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    @SerialName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false
)

@Serializable
data class WorkflowsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<GitHubWorkflow> = emptyList()
)

@Serializable
data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)
