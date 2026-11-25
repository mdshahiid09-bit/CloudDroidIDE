package com.clouddroidide.android.terminal

import android.content.Context
import android.system.Os
import com.clouddroidide.android.core.config.AppConfig
import com.clouddroidide.android.core.config.SettingsRepository
import com.clouddroidide.android.download.DownloadManager
import com.clouddroidide.android.download.DownloadPurpose
import com.clouddroidide.android.download.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class LinuxEnvPhase {
    NOT_INSTALLED,
    DOWNLOADING,
    EXTRACTING,
    BOOTSTRAPPING_PACKAGES,
    READY,
    ERROR
}

data class LinuxEnvState(
    val phase: LinuxEnvPhase = LinuxEnvPhase.NOT_INSTALLED,
    val statusDetail: String = "AArch64 Alpine environment not installed",
    val downloadTaskId: String? = null,
    val hasBash: Boolean = false,
    val hasGit: Boolean = false,
    val hasAgy: Boolean = false
)

/**
 * Manages the AArch64 Alpine Linux rootfs + PRoot container in filesDir/linux_env,
 * bootstrapping bash, git, gh, nodejs, python3, and user-configured `agy` CLI.
 */
class LinuxEnvironmentManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager
) {
    val envRootDir = File(context.filesDir, AppConfig.LINUX_ENV_DIR_NAME)
    val rootfsDir = File(envRootDir, "rootfs")
    val tmpDir = File(envRootDir, "tmp")
    val binDir = File(envRootDir, "bin")
    val projectsDir = File(context.filesDir, AppConfig.PROJECTS_DIR_NAME)

    private val bootstrapMarker = File(envRootDir, ".bootstrapped")

    private val _state = MutableStateFlow(computeInitialState())
    val state: StateFlow<LinuxEnvState> = _state.asStateFlow()

    init {
        projectsDir.mkdirs()
        tmpDir.mkdirs()
        binDir.mkdirs()
    }

    fun refreshState() {
        _state.value = computeInitialState()
    }

    private fun computeInitialState(): LinuxEnvState {
        val shFile = File(rootfsDir, "bin/sh")
        if (!shFile.exists()) {
            return LinuxEnvState(
                phase = LinuxEnvPhase.NOT_INSTALLED,
                statusDetail = "AArch64 Linux rootfs not installed"
            )
        }
        val hasBash = File(rootfsDir, "bin/bash").exists() || File(rootfsDir, "usr/bin/bash").exists()
        val hasGit = File(rootfsDir, "usr/bin/git").exists()
        val hasAgy = File(rootfsDir, "usr/local/bin/agy").exists() ||
            File(rootfsDir, "usr/bin/agy").exists()
        return if (bootstrapMarker.exists() || hasGit) {
            LinuxEnvState(
                phase = LinuxEnvPhase.READY,
                statusDetail = "AArch64 Alpine Linux ready",
                hasBash = hasBash,
                hasGit = hasGit,
                hasAgy = hasAgy
            )
        } else {
            LinuxEnvState(
                phase = LinuxEnvPhase.EXTRACTING,
                statusDetail = "Rootfs extracted; package bootstrap pending",
                hasBash = hasBash,
                hasGit = hasGit,
                hasAgy = hasAgy
            )
        }
    }

    fun resolveProotExecutable(): File {
        val nativeProot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        if (nativeProot.exists()) return nativeProot
        return File(binDir, "proot")
    }

    suspend fun installAndBootstrap(
        onLog: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.current()
            envRootDir.mkdirs()
            rootfsDir.mkdirs()
            tmpDir.mkdirs()
            binDir.mkdirs()
            projectsDir.mkdirs()

            val archiveDir = File(context.filesDir, AppConfig.DOWNLOADS_DIR_NAME).apply { mkdirs() }
            val rootfsArchive = File(archiveDir, "alpine-minirootfs-aarch64.tar.gz")
            val prootArchive = File(archiveDir, "proot-static-aarch64.apk")

            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.DOWNLOADING,
                statusDetail = "Downloading Alpine AArch64 rootfs..."
            )
            onLog("Downloading rootfs from ${settings.rootfsUrl}")
            val rootfsTaskId = downloadManager.enqueue(
                title = "Alpine AArch64 Rootfs",
                url = settings.rootfsUrl,
                destinationFile = rootfsArchive,
                purpose = DownloadPurpose.LINUX_ROOTFS
            )
            _state.value = _state.value.copy(downloadTaskId = rootfsTaskId)
            awaitDownload(rootfsTaskId)

            _state.value = _state.value.copy(statusDetail = "Downloading PRoot static binary...")
            onLog("Downloading PRoot from ${settings.prootUrl}")
            val prootTaskId = downloadManager.enqueue(
                title = "PRoot AArch64 Static",
                url = settings.prootUrl,
                destinationFile = prootArchive,
                purpose = DownloadPurpose.PROOT_BINARY
            )
            _state.value = _state.value.copy(downloadTaskId = prootTaskId)
            awaitDownload(prootTaskId)

            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.EXTRACTING,
                statusDetail = "Extracting Alpine rootfs...",
                downloadTaskId = null
            )
            onLog("Extracting rootfs into ${rootfsDir.absolutePath}")
            extractTarGz(rootfsArchive, rootfsDir)
            extractProotBinary(prootArchive, File(binDir, "proot"))
            writeRootfsDefaults(settings.agyInstallSource)

            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.BOOTSTRAPPING_PACKAGES,
                statusDetail = "Installing bash, git, gh, nodejs, python3..."
            )
            onLog("Bootstrapping Alpine packages via PRoot...")
            val code = runProotCommand(
                command = "/bin/sh /usr/local/bin/clouddroid-bootstrap.sh",
                onOutput = onLog
            )
            if (code != 0) {
                error("Bootstrap script exited with code $code")
            }
            bootstrapMarker.writeText("ok\n")
            refreshState()
        }.onFailure { err ->
            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.ERROR,
                statusDetail = err.message ?: "Environment setup failed"
            )
        }
    }

    suspend fun installAgyCli(sourceInput: String, onLog: (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = sourceInput.trim()
                require(trimmed.isNotBlank()) {
                    "Enter an agy GitHub release URL, install script URL, or npm package name"
                }
                settingsRepository.updateSettings { it.copy(agyInstallSource = trimmed) }
                writeAgyInstallScript(trimmed)
                val code = runProotCommand(
                    command = "/bin/sh /usr/local/bin/install-agy.sh",
                    onOutput = onLog
                )
                if (code != 0) {
                    error("agy installation exited with code $code")
                }
                refreshState()
            }
        }

    fun buildInteractiveShellArgs(): Pair<String, Array<String>> {
        val proot = resolveProotExecutable().absolutePath
        val shell = if (File(rootfsDir, "bin/bash").exists() || File(rootfsDir, "usr/bin/bash").exists()) {
            "/bin/bash"
        } else {
            "/bin/sh"
        }
        val args = arrayOf(
            "-0",
            "--link2symlink",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${projectsDir.absolutePath}:/workspace",
            "-w", "/workspace",
            shell,
            "-l"
        )
        return proot to args
    }

    fun buildProotEnv(extraEnv: Map<String, String> = emptyMap()): Array<String> {
        val merged = linkedMapOf(
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1"
        )
        merged.putAll(extraEnv)
        return merged.entries.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    fun createInteractiveSession(cwd: String = projectsDir.absolutePath): TerminalSession {
        val (cmd, args) = buildInteractiveShellArgs()
        return TerminalSession(
            command = cmd,
            args = args,
            cwd = cwd,
            env = buildProotEnv()
        )
    }

    fun runProotCommand(
        command: String,
        cwd: String = "/workspace",
        extraEnv: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {}
    ): Int {
        val proot = resolveProotExecutable().absolutePath
        val args = listOf(
            proot,
            "-0",
            "--link2symlink",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${projectsDir.absolutePath}:/workspace",
            "-w", cwd,
            "/bin/sh",
            "-c",
            command
        )
        val pb = ProcessBuilder(args)
            .directory(projectsDir)
            .redirectErrorStream(true)
        val envMap = pb.environment()
        envMap["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        envMap["HOME"] = "/root"
        envMap["TERM"] = "xterm-256color"
        envMap["PROOT_TMP_DIR"] = tmpDir.absolutePath
        envMap["PROOT_NO_SECCOMP"] = "1"
        extraEnv.forEach { (k, v) -> envMap[k] = v }

        val process = pb.start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line -> onOutput(line) }
        }
        return process.waitFor()
    }

    private suspend fun awaitDownload(taskId: String) {
        val finalState = downloadManager.tasks
            .first { map ->
                val st = map[taskId]?.status
                st == DownloadStatus.COMPLETED || st == DownloadStatus.FAILED || st == DownloadStatus.CANCELLED
            }[taskId]
        if (finalState?.status != DownloadStatus.COMPLETED) {
            error(finalState?.errorMessage ?: "Download failed ($taskId)")
        }
    }

    private fun extractTarGz(archive: File, targetDir: File) {
        val rootCanonical = targetDir.canonicalPath
        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tais ->
                        var entry: TarArchiveEntry? = tais.nextEntry
                        while (entry != null) {
                            val outFile = File(targetDir, entry.name)
                            if (!outFile.canonicalPath.startsWith(rootCanonical)) {
                                entry = tais.nextEntry
                                continue
                            }
                            when {
                                entry.isDirectory -> {
                                    outFile.mkdirs()
                                }
                                entry.isSymbolicLink -> {
                                    outFile.parentFile?.mkdirs()
                                    if (outFile.exists()) outFile.delete()
                                    runCatching { Os.symlink(entry.linkName, outFile.absolutePath) }
                                }
                                else -> {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        tais.copyTo(fos)
                                    }
                                    val isExec = (entry.mode and 0b001001001) != 0
                                    if (isExec) {
                                        outFile.setExecutable(true, false)
                                    }
                                }
                            }
                            entry = tais.nextEntry
                        }
                    }
                }
            }
        }
        listOf("bin", "sbin", "usr/bin", "usr/sbin", "lib").forEach { rel ->
            File(targetDir, rel).walkTopDown().forEach { f ->
                if (f.isFile) f.setExecutable(true, false)
            }
        }
    }

    private fun extractProotBinary(apkTarGz: File, destProot: File) {
        FileInputStream(apkTarGz).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tais ->
                        var entry: TarArchiveEntry? = tais.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith("proot.static") || entry.name.endsWith("/proot")) {
                                destProot.parentFile?.mkdirs()
                                FileOutputStream(destProot).use { fos ->
                                    tais.copyTo(fos)
                                }
                                destProot.setExecutable(true, false)
                                return
                            }
                            entry = tais.nextEntry
                        }
                    }
                }
            }
        }
    }

    private fun writeRootfsDefaults(agySource: String) {
        val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
        File(etcDir, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(etcDir, "profile").writeText(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            export TERM=xterm-256color
            export PS1='\u@clouddroid:\w\$ '
            """.trimIndent() + "\n"
        )
        val localBin = File(rootfsDir, "usr/local/bin").apply { mkdirs() }
        val bootstrapScript = File(localBin, "clouddroid-bootstrap.sh")
        bootstrapScript.writeText(
            """
            #!/bin/sh
            set -e
            apk update
            apk add --no-cache bash git github-cli nodejs npm python3 curl ca-certificates openssh-client tar gzip
            mkdir -p /root /workspace
            echo "Alpine AArch64 toolchain ready."
            """.trimIndent() + "\n"
        )
        bootstrapScript.setExecutable(true, false)
        if (agySource.isNotBlank()) {
            writeAgyInstallScript(agySource)
        }
    }

    private fun writeAgyInstallScript(source: String) {
        val localBin = File(rootfsDir, "usr/local/bin").apply { mkdirs() }
        val scriptFile = File(localBin, "install-agy.sh")
        val body = when {
            source.endsWith(".sh", ignoreCase = true) -> """
                #!/bin/sh
                set -e
                curl -fsSL "$source" | sh
            """.trimIndent()
            source.endsWith(".tar.gz", ignoreCase = true) || source.endsWith(".tgz", ignoreCase = true) -> """
                #!/bin/sh
                set -e
                mkdir -p /tmp/agy_dl
                curl -fsSL "$source" -o /tmp/agy_dl/agy.tar.gz
                tar -xzf /tmp/agy_dl/agy.tar.gz -C /usr/local/bin
                chmod 755 /usr/local/bin/agy* 2>/dev/null || true
                rm -rf /tmp/agy_dl
            """.trimIndent()
            source.startsWith("http://") || source.startsWith("https://") -> """
                #!/bin/sh
                set -e
                curl -fsSL "$source" -o /usr/local/bin/agy
                chmod 755 /usr/local/bin/agy
            """.trimIndent()
            else -> """
                #!/bin/sh
                set -e
                npm install -g "$source"
            """.trimIndent()
        }
        scriptFile.writeText(body + "\n")
        scriptFile.setExecutable(true, false)
    }
}
