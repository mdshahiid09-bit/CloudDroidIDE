package com.clouddroidide.android.data.remote

import com.clouddroidide.android.core.config.AppConfig
import com.clouddroidide.android.core.crypto.LibsodiumSealedBox
import com.clouddroidide.android.core.security.SecureStorage
import com.clouddroidide.android.data.remote.model.DeviceCodeResponse
import com.clouddroidide.android.data.remote.model.GitHubRelease
import com.clouddroidide.android.data.remote.model.GitHubRepo
import com.clouddroidide.android.data.remote.model.GitHubUser
import com.clouddroidide.android.data.remote.model.PutSecretRequest
import com.clouddroidide.android.data.remote.model.WorkflowArtifact
import com.clouddroidide.android.data.remote.model.WorkflowDispatchRequest
import com.clouddroidide.android.data.remote.model.WorkflowJob
import com.clouddroidide.android.data.remote.model.WorkflowRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

data class GitHubAuthState(
    val isAuthenticated: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null
)

class GitHubRepository(
    private val secureStorage: SecureStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "CloudDroidIDE-Android/1.0")
            val token = secureStorage.getGitHubToken()
            if (!token.isNullOrBlank() && original.url.host == "api.github.com") {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val api: GitHubApiService = Retrofit.Builder()
        .baseUrl(AppConfig.GITHUB_API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApiService::class.java)

    private val oauthOkHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "CloudDroidIDE-Android/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    private val oauthApi: GitHubOAuthService = Retrofit.Builder()
        .baseUrl(AppConfig.GITHUB_WEB_BASE_URL)
        .client(oauthOkHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubOAuthService::class.java)

    private val _authState = MutableStateFlow(
        GitHubAuthState(
            isAuthenticated = secureStorage.getGitHubToken() != null,
            username = secureStorage.getGitHubUsername(),
            avatarUrl = secureStorage.getGitHubAvatarUrl()
        )
    )
    val authState: StateFlow<GitHubAuthState> = _authState.asStateFlow()

    fun getToken(): String? = secureStorage.getGitHubToken()

    suspend fun authenticateWithPat(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = token.trim()
            require(clean.isNotBlank()) { "Personal Access Token cannot be empty" }
            secureStorage.setGitHubToken(clean)
            try {
                val user = api.getAuthenticatedUser()
                secureStorage.setGitHubUsername(user.login)
                secureStorage.setGitHubAvatarUrl(user.avatarUrl)
                _authState.value = GitHubAuthState(true, user.login, user.avatarUrl)
                user
            } catch (t: Throwable) {
                secureStorage.clearAuth()
                _authState.value = GitHubAuthState(false, null, null)
                throw t
            }
        }
    }

    suspend fun startDeviceFlow(clientId: String): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(clientId.isNotBlank()) { "GitHub OAuth Client ID is not configured in Settings" }
            oauthApi.requestDeviceCode(clientId.trim())
        }
    }

    suspend fun pollDeviceToken(clientId: String, deviceCode: String): Result<GitHubUser?> = withContext(Dispatchers.IO) {
        runCatching {
            val res = oauthApi.pollAccessToken(clientId.trim(), deviceCode)
            when {
                !res.accessToken.isNullOrBlank() -> {
                    secureStorage.setGitHubToken(res.accessToken)
                    val user = api.getAuthenticatedUser()
                    secureStorage.setGitHubUsername(user.login)
                    secureStorage.setGitHubAvatarUrl(user.avatarUrl)
                    _authState.value = GitHubAuthState(true, user.login, user.avatarUrl)
                    user
                }
                res.error == "authorization_pending" || res.error == "slow_down" -> null
                else -> error(res.errorDescription ?: res.error ?: "OAuth failed")
            }
        }
    }

    fun logout() {
        secureStorage.clearAuth()
        _authState.value = GitHubAuthState(false, null, null)
    }

    suspend fun refreshUserIfLoggedIn(): Result<GitHubUser?> = withContext(Dispatchers.IO) {
        runCatching {
            if (secureStorage.getGitHubToken() == null) return@runCatching null
            val user = api.getAuthenticatedUser()
            secureStorage.setGitHubUsername(user.login)
            secureStorage.setGitHubAvatarUrl(user.avatarUrl)
            _authState.value = GitHubAuthState(true, user.login, user.avatarUrl)
            user
        }
    }

    suspend fun listRepos(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        runCatching { api.listUserRepos() }
    }

    suspend fun createRepo(name: String, description: String, isPrivate: Boolean): Result<GitHubRepo> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.createRepo(
                    com.clouddroidide.android.data.remote.model.CreateRepoRequest(
                        name = name.trim(),
                        description = description.trim(),
                        private = isPrivate
                    )
                )
            }
        }

    suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getLatestRelease(owner.trim(), repo.trim())
                if (response.code() == 404) return@runCatching null
                if (!response.isSuccessful) {
                    error("GitHub Releases API error: HTTP ${response.code()}")
                }
                response.body()
            }
        }

    suspend fun pushEncryptedSecret(
        owner: String,
        repo: String,
        secretName: String,
        secretValue: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pk = api.getActionsPublicKey(owner.trim(), repo.trim())
            val encrypted = LibsodiumSealedBox.encryptSecret(secretValue, pk.key)
            val resp = api.putActionsSecret(
                owner = owner.trim(),
                repo = repo.trim(),
                secretName = secretName,
                request = PutSecretRequest(encryptedValue = encrypted, keyId = pk.keyId)
            )
            if (!resp.isSuccessful) {
                error("Failed to upload secret $secretName: HTTP ${resp.code()}")
            }
        }
    }

    suspend fun triggerBuildWorkflow(
        owner: String,
        repo: String,
        ref: String = "main",
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val workflows = api.listWorkflows(owner.trim(), repo.trim()).workflows
            val target = workflows.firstOrNull {
                it.path.endsWith("build.yml") || it.path.endsWith("build.yaml")
            } ?: workflows.firstOrNull()
            ?: error("No GitHub Actions workflow found in $owner/$repo (.github/workflows/build.yml)")

            val resp = api.dispatchWorkflow(
                owner = owner.trim(),
                repo = repo.trim(),
                workflowId = target.id.toString(),
                request = WorkflowDispatchRequest(ref = ref, inputs = inputs)
            )
            if (!resp.isSuccessful) {
                error("Workflow dispatch failed: HTTP ${resp.code()}")
            }
        }
    }

    suspend fun listWorkflowRuns(owner: String, repo: String): Result<List<WorkflowRun>> =
        withContext(Dispatchers.IO) {
            runCatching { api.listWorkflowRuns(owner.trim(), repo.trim()).workflowRuns }
        }

    suspend fun listRunJobs(owner: String, repo: String, runId: Long): Result<List<WorkflowJob>> =
        withContext(Dispatchers.IO) {
            runCatching { api.listRunJobs(owner.trim(), repo.trim(), runId).jobs }
        }

    suspend fun fetchJobLogs(owner: String, repo: String, jobId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${AppConfig.GITHUB_API_BASE_URL}repos/${owner.trim()}/${repo.trim()}/actions/jobs/$jobId/logs"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("Unable to fetch job logs: HTTP ${resp.code}")
                    }
                    resp.body?.string().orEmpty()
                }
            }
        }

    suspend fun listRunArtifacts(owner: String, repo: String, runId: Long): Result<List<WorkflowArtifact>> =
        withContext(Dispatchers.IO) {
            runCatching { api.listRunArtifacts(owner.trim(), repo.trim(), runId).artifacts }
        }
}
