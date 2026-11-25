package com.clouddroidide.android.workspace

import com.clouddroidide.android.core.security.SecureStorage
import com.clouddroidide.android.terminal.LinuxEnvPhase
import com.clouddroidide.android.terminal.LinuxEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GitRepoStatus(
    val branch: String,
    val remoteUrl: String,
    val changedFiles: List<String>,
    val branches: List<String>
)

/**
 * Executes real Git operations inside the AArch64 Alpine PRoot environment,
 * injecting GitHub PAT via temporary GIT_ASKPASS without persisting tokens on disk.
 */
class GitEngine(
    private val envManager: LinuxEnvironmentManager,
    private val secureStorage: SecureStorage
) {
    private fun ensureEnvReady() {
        envManager.refreshState()
        val st = envManager.state.value
        if (st.phase != LinuxEnvPhase.READY || !st.hasGit) {
            error("Install AArch64 Linux Environment (with git) in Terminal Setup first")
        }
    }

    private fun workspacePathFor(projectDir: File): String {
        val rootPath = envManager.projectsDir.absolutePath
        val rel = projectDir.absolutePath.removePrefix(rootPath).trimStart('/')
        return if (rel.isEmpty()) "/workspace" else "/workspace/$rel"
    }

    private fun runGit(
        cwdInProot: String,
        gitCommand: String,
        useToken: Boolean = true
    ): Pair<Int, String> {
        ensureEnvReady()
        val out = StringBuilder()
        val token = if (useToken) secureStorage.getGitHubToken() else null
        val askPassHost = File(envManager.tmpDir, "git-askpass.sh")
        val extraEnv = mutableMapOf<String, String>()

        if (!token.isNullOrBlank()) {
            askPassHost.writeText("#!/bin/sh\necho \"$token\"\n")
            askPassHost.setExecutable(true, false)
            // Bind /tmp inside proot via rootfs /tmp or write inside rootfs /tmp
            val askPassInRootfs = File(envManager.rootfsDir, "tmp/cd_askpass.sh")
            askPassInRootfs.parentFile?.mkdirs()
            askPassInRootfs.writeText("#!/bin/sh\necho \"$token\"\n")
            askPassInRootfs.setExecutable(true, false)
            extraEnv["GIT_ASKPASS"] = "/tmp/cd_askpass.sh"
            extraEnv["GIT_TERMINAL_PROMPT"] = "0"
        }

        val fullCmd = if (!token.isNullOrBlank()) {
            "git -c credential.helper= $gitCommand"
        } else {
            "git $gitCommand"
        }

        val code = try {
            envManager.runProotCommand(
                command = fullCmd,
                cwd = cwdInProot,
                extraEnv = extraEnv
            ) { line -> out.appendLine(line) }
        } finally {
            File(envManager.rootfsDir, "tmp/cd_askpass.sh").delete()
            askPassHost.delete()
        }
        return code to out.toString().trim()
    }

    suspend fun cloneRepository(repoUrl: String, targetFolderName: String? = null): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanUrl = repoUrl.trim()
                require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("git@")) {
                    "Enter a valid Git repository HTTPS URL"
                }
                val folderName = targetFolderName?.takeIf { it.isNotBlank() }
                    ?: cleanUrl.substringAfterLast('/').removeSuffix(".git")
                val dest = File(envManager.projectsDir, folderName)
                require(!dest.exists()) { "Destination folder '$folderName' already exists" }

                val quotedUrl = "'${cleanUrl.replace("'", "'\\''")}'"
                val quotedFolder = "'${folderName.replace("'", "'\\''")}'"
                val (code, output) = runGit("/workspace", "clone $quotedUrl $quotedFolder")
                if (code != 0) {
                    error(output.ifBlank { "git clone failed with code $code" })
                }
                dest
            }
        }

    suspend fun getStatus(projectDir: File): Result<GitRepoStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val cwd = workspacePathFor(projectDir)
            if (!File(projectDir, ".git").exists()) {
                return@runCatching GitRepoStatus(
                    branch = "not initialized",
                    remoteUrl = "",
                    changedFiles = emptyList(),
                    branches = emptyList()
                )
            }
            val (_, statusOut) = runGit(cwd, "status --porcelain", useToken = false)
            val (_, branchOut) = runGit(cwd, "rev-parse --abbrev-ref HEAD", useToken = false)
            val (_, remoteOut) = runGit(cwd, "remote get-url origin", useToken = false)
            val (_, branchesOut) = runGit(cwd, "branch --format='%(refname:short)'", useToken = false)

            GitRepoStatus(
                branch = branchOut.ifBlank { "main" },
                remoteUrl = remoteOut,
                changedFiles = statusOut.lines().filter { it.isNotBlank() },
                branches = branchesOut.lines().map { it.trim().trim('\'') }.filter { it.isNotBlank() }
            )
        }
    }

    suspend fun initAndSetRemote(
        projectDir: File,
        remoteUrl: String,
        userName: String,
        userEmail: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cwd = workspacePathFor(projectDir)
            val safeName = userName.ifBlank { "CloudDroid Developer" }
            val safeEmail = userEmail.ifBlank { "dev@clouddroidide.local" }
            val cmd = buildString {
                append("init -b main && ")
                append("git config user.name '$safeName' && ")
                append("git config user.email '$safeEmail'")
                if (remoteUrl.isNotBlank()) {
                    append(" && (git remote set-url origin '$remoteUrl' || git remote add origin '$remoteUrl')")
                }
            }
            val (code, out) = runGit(cwd, cmd, useToken = false)
            if (code != 0) error(out.ifBlank { "git init failed" })
            out.ifBlank { "Initialized Git repository on branch main" }
        }
    }

    suspend fun stageAllAndCommit(
        projectDir: File,
        message: String,
        authorName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(message.isNotBlank()) { "Commit message cannot be empty" }
            val cwd = workspacePathFor(projectDir)
            val safeMsg = message.replace("'", "'\\''")
            val safeUser = authorName.ifBlank { "CloudDroid User" }.replace("'", "'\\''")
            val cmd = "-c user.name='$safeUser' -c user.email='${safeUser}@users.noreply.github.com' add -A && " +
                "git -c user.name='$safeUser' -c user.email='${safeUser}@users.noreply.github.com' commit -m '$safeMsg'"
            val (code, out) = runGit(cwd, cmd, useToken = false)
            if (code != 0) error(out.ifBlank { "git commit failed" })
            out
        }
    }

    suspend fun push(projectDir: File, branch: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cwd = workspacePathFor(projectDir)
            val targetBranch = branch.ifBlank { "main" }
            val (code, out) = runGit(cwd, "push -u origin '$targetBranch'")
            if (code != 0) error(out.ifBlank { "git push failed" })
            out.ifBlank { "Pushed to origin/$targetBranch" }
        }
    }

    suspend fun pull(projectDir: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cwd = workspacePathFor(projectDir)
            val (code, out) = runGit(cwd, "pull")
            if (code != 0) error(out.ifBlank { "git pull failed" })
            out.ifBlank { "Repository is up to date" }
        }
    }

    suspend fun checkoutBranch(projectDir: File, branch: String, create: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(branch.isNotBlank()) { "Branch name required" }
                val cwd = workspacePathFor(projectDir)
                val flag = if (create) "-b " else ""
                val (code, out) = runGit(cwd, "checkout $flag'${branch.trim()}'", useToken = false)
                if (code != 0) error(out.ifBlank { "git checkout failed" })
                out.ifBlank { "Switched to $branch" }
            }
        }
}
