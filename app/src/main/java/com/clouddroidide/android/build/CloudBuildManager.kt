package com.clouddroidide.android.build

import android.content.Context
import com.clouddroidide.android.core.config.AppConfig
import com.clouddroidide.android.data.remote.GitHubRepository
import com.clouddroidide.android.data.remote.model.WorkflowArtifact
import com.clouddroidide.android.data.remote.model.WorkflowJob
import com.clouddroidide.android.data.remote.model.WorkflowRun
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadPurpose
import com.clouddroidide.android.download.DownloadStatus
import com.clouddroidide.android.utils.DiagnosticParser
import com.clouddroidide.android.workspace.BuildDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class CloudBuildState(
    val selectedRepoFullName: String = "",
    val runs: List<WorkflowRun> = emptyList(),
    val selectedRun: WorkflowRun? = null,
    val jobs: List<WorkflowJob> = emptyList(),
    val artifacts: List<WorkflowArtifact> = emptyList(),
    val logsText: String = "",
    val diagnostics: List<BuildDiagnostic> = emptyList(),
    val extractedFiles: List<File> = emptyList(),
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Orchestrates GitHub Actions Cloud Builds: dispatch, live job polling, log streaming,
 * diagnostic parsing, and artifact ZIP download + extraction.
 */
class CloudBuildManager(
    private val context: Context,
    private val gitHubRepository: GitHubRepository,
    private val downloadManager: DownloadManager
) {
    private val _state = MutableStateFlow(CloudBuildState())
    val state: StateFlow<CloudBuildState> = _state.asStateFlow()

    fun setRepo(fullName: String) {
        _state.value = _state.value.copy(selectedRepoFullName = fullName.trim())
    }

    private fun parseOwnerRepo(fullName: String): Pair<String, String>? {
        val parts = fullName.trim().split("/")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }

    suspend fun refreshRuns(repoFullName: String = _state.value.selectedRepoFullName) {
        val (owner, repo) = parseOwnerRepo(repoFullName) ?: run {
            _state.value = _state.value.copy(errorMessage = "Select a GitHub repository (owner/repo)")
            return
        }
        _state.value = _state.value.copy(
            selectedRepoFullName = "$owner/$repo",
            isLoading = true,
            errorMessage = null
        )
        gitHubRepository.listWorkflowRuns(owner, repo)
            .onSuccess { runs ->
                _state.value = _state.value.copy(
                    runs = runs,
                    isLoading = false,
                    selectedRun = _state.value.selectedRun?.let { cur -> runs.find { it.id == cur.id } } ?: runs.firstOrNull()
                )
                _state.value.selectedRun?.let { loadRunDetails(owner, repo, it) }
            }
            .onFailure { err ->
                _state.value = _state.value.copy(isLoading = false, errorMessage = err.message)
            }
    }

    suspend fun triggerAndTrack(
        repoFullName: String,
        branch: String = "main",
        playTrack: String = ""
    ): Result<Unit> = runCatching {
        val (owner, repo) = parseOwnerRepo(repoFullName)
            ?: error("Select a valid owner/repo first")
        _state.value = _state.value.copy(
            selectedRepoFullName = "$owner/$repo",
            isLoading = true,
            errorMessage = null
        )
        val inputs = if (playTrack.isNotBlank()) mapOf("play_track" to playTrack) else emptyMap()
        gitHubRepository.triggerBuildWorkflow(owner, repo, ref = branch, inputs = inputs).getOrThrow()

        // Wait briefly for GitHub to register the new run, then poll
        delay(2500L)
        refreshRuns("$owner/$repo")
        pollLatestRun(owner, repo)
    }.onFailure { err ->
        _state.value = _state.value.copy(isLoading = false, isPolling = false, errorMessage = err.message)
    }

    suspend fun pollLatestRun(owner: String, repo: String) {
        _state.value = _state.value.copy(isPolling = true)
        repeat(60) {
            val runs = gitHubRepository.listWorkflowRuns(owner, repo).getOrNull() ?: return@repeat
            val latest = runs.firstOrNull()
            _state.value = _state.value.copy(runs = runs, selectedRun = latest)
            if (latest != null) {
                loadRunDetails(owner, repo, latest)
                if (latest.status == "completed") {
                    _state.value = _state.value.copy(isPolling = false)
                    return
                }
            }
            delay(6000L)
        }
        _state.value = _state.value.copy(isPolling = false)
    }

    suspend fun selectRun(run: WorkflowRun) {
        val (owner, repo) = parseOwnerRepo(_state.value.selectedRepoFullName) ?: return
        _state.value = _state.value.copy(selectedRun = run)
        loadRunDetails(owner, repo, run)
    }

    private suspend fun loadRunDetails(owner: String, repo: String, run: WorkflowRun) {
        val jobs = gitHubRepository.listRunJobs(owner, repo, run.id).getOrNull().orEmpty()
        val artifacts = gitHubRepository.listRunArtifacts(owner, repo, run.id).getOrNull().orEmpty()
        val firstJob = jobs.firstOrNull()
        val logs = if (firstJob != null) {
            gitHubRepository.fetchJobLogs(owner, repo, firstJob.id).getOrNull().orEmpty()
        } else {
            ""
        }
        val diagnostics = DiagnosticParser.parse(logs)
        val runExtractDir = File(context.filesDir, "${AppConfig.ARTIFACTS_DIR_NAME}/${run.id}")
        val existingExtracted = if (runExtractDir.exists()) {
            runExtractDir.walkTopDown().filter { it.isFile && (it.extension == "apk" || it.extension == "aab") }.toList()
        } else {
            emptyList()
        }

        _state.value = _state.value.copy(
            jobs = jobs,
            artifacts = artifacts,
            logsText = logs,
            diagnostics = diagnostics,
            extractedFiles = existingExtracted
        )
    }

    suspend fun downloadAndExtractArtifact(runId: Long, artifact: WorkflowArtifact): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outDir = File(context.filesDir, "${AppConfig.ARTIFACTS_DIR_NAME}/$runId").apply { mkdirs() }
                val zipFile = File(outDir, "${artifact.name}.zip")
                val taskId = downloadManager.enqueue(
                    title = "${artifact.name}.zip",
                    url = artifact.archiveDownloadUrl,
                    destinationFile = zipFile,
                    purpose = DownloadPurpose.BUILD_ARTIFACT,
                    requiresGitHubAuth = true
                )
                val finalState = downloadManager.tasks.first { map ->
                    val s = map[taskId]?.status
                    s == DownloadStatus.COMPLETED || s == DownloadStatus.FAILED || s == DownloadStatus.CANCELLED
                }[taskId]
                if (finalState?.status != DownloadStatus.COMPLETED) {
                    error(finalState?.errorMessage ?: "Artifact download failed")
                }

                val extracted = mutableListOf<File>()
                val canonicalRoot = outDir.canonicalPath
                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val dest = File(outDir, entry.name)
                        if (dest.canonicalPath.startsWith(canonicalRoot) && !entry.isDirectory) {
                            dest.parentFile?.mkdirs()
                            FileOutputStream(dest).use { fos -> zis.copyTo(fos) }
                            extracted += dest
                        }
                        entry = zis.nextEntry
                    }
                }
                val apksAndAabs = extracted.filter {
                    it.extension.equals("apk", ignoreCase = true) ||
                        it.extension.equals("aab", ignoreCase = true)
                }
                _state.value = _state.value.copy(extractedFiles = apksAndAabs)
                apksAndAabs
            }
        }
}
