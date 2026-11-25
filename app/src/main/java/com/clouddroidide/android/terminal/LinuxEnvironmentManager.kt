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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    private fun existsOrSymlink(file: File): Boolean =
        file.exists() || Files.isSymbolicLink(file.toPath())

    private fun computeInitialState(): LinuxEnvState {
        val shFile = File(rootfsDir, "bin/sh")
        val busyboxFile = File(rootfsDir, "bin/busybox")
        if (!existsOrSymlink(shFile) && !existsOrSymlink(busyboxFile)) {
            return LinuxEnvState(
                phase = LinuxEnvPhase.NOT_INSTALLED,
                statusDetail = "AArch64 Linux rootfs not installed"
            )
        }
        val hasBash = existsOrSymlink(File(rootfsDir, "bin/bash")) ||
            existsOrSymlink(File(rootfsDir, "usr/bin/bash"))
        val hasGit = existsOrSymlink(File(rootfsDir, "usr/bin/git"))
        val hasAgy = existsOrSymlink(File(rootfsDir, "usr/local/bin/agy")) ||
            existsOrSymlink(File(rootfsDir, "usr/bin/agy"))
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
                statusDetail = "Checking Alpine AArch64 rootfs URL..."
            )
            val resolvedRootfsUrl = resolveWorkingRootfsUrl(settings.rootfsUrl, onLog)
            if (resolvedRootfsUrl != settings.rootfsUrl) {
                settingsRepository.updateSettings { it.copy(rootfsUrl = resolvedRootfsUrl) }
            }

            _state.value = _state.value.copy(statusDetail = "Downloading Alpine AArch64 rootfs...")
            onLog("Downloading rootfs from $resolvedRootfsUrl")
            val rootfsTaskId = downloadManager.enqueue(
                title = "Alpine AArch64 Rootfs",
                url = resolvedRootfsUrl,
                destinationFile = rootfsArchive,
                purpose = DownloadPurpose.LINUX_ROOTFS
            )
            _state.value = _state.value.copy(downloadTaskId = rootfsTaskId)
            awaitDownload(rootfsTaskId)

            val nativeProotAvailable = File(context.applicationInfo.nativeLibraryDir, "libproot.so").exists()
            val resolvedProotUrl = resolveWorkingProotUrl(settings.prootUrl, onLog)
            if (resolvedProotUrl != null) {
                if (resolvedProotUrl != settings.prootUrl) {
                    settingsRepository.updateSettings { it.copy(prootUrl = resolvedProotUrl) }
                }
                _state.value = _state.value.copy(statusDetail = "Downloading PRoot static binary...")
                onLog("Downloading PRoot from $resolvedProotUrl")
                val prootTaskId = downloadManager.enqueue(
                    title = "PRoot AArch64 Static",
                    url = resolvedProotUrl,
                    destinationFile = prootArchive,
                    purpose = DownloadPurpose.PROOT_BINARY
                )
                _state.value = _state.value.copy(downloadTaskId = prootTaskId)
                val prootResult = runCatching { awaitDownload(prootTaskId) }
                if (prootResult.isFailure && !nativeProotAvailable) {
                    throw prootResult.exceptionOrNull()!!
                } else if (prootResult.isFailure) {
                    onLog("Warning: PRoot download failed, using bundled arm64 libproot.so")
                }
            } else if (!nativeProotAvailable) {
                error("PRoot URL unreachable (${settings.prootUrl}) and no bundled libproot.so found")
            } else {
                onLog("Using bundled arm64 libproot.so")
            }

            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.EXTRACTING,
                statusDetail = "Extracting Alpine rootfs...",
                downloadTaskId = null
            )
            onLog("Extracting rootfs into ${rootfsDir.absolutePath}")
            extractTarGz(rootfsArchive, rootfsDir)
            if (prootArchive.exists() && prootArchive.length() > 1024L) {
                runCatching { extractProotBinary(prootArchive, File(binDir, "proot")) }
            }
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

            if (settings.agyInstallSource.isNotBlank()) {
                _state.value = _state.value.copy(statusDetail = "Installing agy CLI...")
                onLog("Installing agy CLI from ${settings.agyInstallSource}...")
                val agyCode = runProotCommand(
                    command = "/bin/sh /usr/local/bin/install-agy.sh",
                    onOutput = onLog
                )
                if (agyCode != 0) {
                    error("agy installer exited with code $agyCode")
                }
            }

            bootstrapMarker.writeText("ok\n")
            refreshState()
        }.onFailure { err ->
            _state.value = _state.value.copy(
                phase = LinuxEnvPhase.ERROR,
                statusDetail = err.message ?: "Environment setup failed",
                downloadTaskId = null
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

    private fun isUrlReachable(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).head().header("User-Agent", "CloudDroidIDE-Android/1.0").build()
        probeClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun resolveWorkingRootfsUrl(configuredUrl: String, onLog: (String) -> Unit): String {
        if (isUrlReachable(configuredUrl)) return configuredUrl
        onLog("Configured rootfs URL unreachable, resolving latest Alpine AArch64 minirootfs...")
        val yamlUrl = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/latest-releases.yaml"
        val body = runCatching {
            val req = Request.Builder().url(yamlUrl).get().build()
            probeClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
        val match = body?.let {
            Regex("""file:\s*(alpine-minirootfs-[^\s]+-aarch64\.tar\.gz)""").find(it)?.groupValues?.getOrNull(1)
        }
        return if (!match.isNullOrBlank()) {
            "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/$match"
        } else {
            configuredUrl
        }
    }

    private fun resolveWorkingProotUrl(configuredUrl: String, onLog: (String) -> Unit): String? {
        if (isUrlReachable(configuredUrl)) return configuredUrl
        onLog("Configured PRoot URL unreachable, checking Alpine APKINDEX...")
        val indexUrl = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/community/aarch64/APKINDEX.tar.gz"
        val version = runCatching {
            val req = Request.Builder().url(indexUrl).get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val stream = resp.body?.byteStream() ?: return@use null
                GzipCompressorInputStream(BufferedInputStream(stream)).use { gzis ->
                    TarArchiveInputStream(gzis).use { tais ->
                        var entry = tais.nextEntry
                        while (entry != null) {
                            if (entry.name == "APKINDEX") {
                                val text = tais.bufferedReader().readText()
                                return@use Regex("""P:proot-static\nV:([^\n]+)""")
                                    .find(text)?.groupValues?.getOrNull(1)
                            }
                            entry = tais.nextEntry
                        }
                        null
                    }
                }
            }
        }.getOrNull()
        return version?.let {
            "https://dl-cdn.alpinelinux.org/alpine/latest-stable/community/aarch64/proot-static-$it.apk"
        }
    }

    fun buildInteractiveShellArgs(): Pair<String, Array<String>> {
        val proot = resolveProotExecutable().absolutePath
        val shell = if (existsOrSymlink(File(rootfsDir, "bin/bash")) ||
            existsOrSymlink(File(rootfsDir, "usr/bin/bash"))
        ) {
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
            "TMPDIR" to "/tmp",
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
        // Clear Android ART/linker env vars that conflict with musl Linux binaries inside PRoot
        envMap.remove("LD_PRELOAD")
        envMap.remove("LD_LIBRARY_PATH")
        envMap.remove("CLASSPATH")
        envMap.remove("BOOTCLASSPATH")
        envMap.remove("DEX2OATBOOTCLASSPATH")
        envMap["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        envMap["HOME"] = "/root"
        envMap["TERM"] = "xterm-256color"
        envMap["LANG"] = "C.UTF-8"
        envMap["TMPDIR"] = "/tmp"
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

    internal fun extractTarGz(archive: File, targetDir: File) {
        val rootCanonical = targetDir.canonicalPath
        val deferredHardLinks = mutableListOf<Pair<File, File>>()

        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tais ->
                        var entry: TarArchiveEntry? = tais.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.removePrefix("./").trimStart('/')
                            if (cleanName.isEmpty() || cleanName == ".") {
                                entry = tais.nextEntry
                                continue
                            }
                            val outFile = File(targetDir, cleanName)
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
                                    runCatching { Files.deleteIfExists(outFile.toPath()) }
                                    runCatching { Os.symlink(entry.linkName, outFile.absolutePath) }
                                }
                                entry.isLink -> {
                                    outFile.parentFile?.mkdirs()
                                    runCatching { Files.deleteIfExists(outFile.toPath()) }
                                    val targetClean = entry.linkName.removePrefix("./").trimStart('/')
                                    val linkTarget = File(targetDir, targetClean)
                                    deferredHardLinks += outFile to linkTarget
                                }
                                else -> {
                                    outFile.parentFile?.mkdirs()
                                    runCatching { Files.deleteIfExists(outFile.toPath()) }
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

        deferredHardLinks.forEach { (outFile, target) ->
            if (target.exists()) {
                runCatching {
                    Files.createLink(outFile.toPath(), target.toPath())
                }.onFailure {
                    target.copyTo(outFile, overwrite = true)
                    if (target.canExecute()) outFile.setExecutable(true, false)
                }
            }
        }

        listOf("bin", "sbin", "usr/bin", "usr/sbin", "lib").forEach { rel ->
            val sub = File(targetDir, rel)
            if (sub.exists()) {
                sub.walkTopDown().forEach { f ->
                    if (f.isFile) f.setExecutable(true, false)
                }
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
                                runCatching { Files.deleteIfExists(destProot.toPath()) }
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
        File(rootfsDir, "tmp").mkdirs()
        File(etcDir, "profile").writeText(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            export TERM=xterm-256color
            export TMPDIR=/tmp
            export PS1='\u@clouddroid:\w\$ '
            """.trimIndent() + "\n"
        )
        val localBin = File(rootfsDir, "usr/local/bin").apply { mkdirs() }
        val bootstrapScript = File(localBin, "clouddroid-bootstrap.sh")
        bootstrapScript.writeText(
            """
            #!/bin/sh
            set -e
            mkdir -p /root /workspace /tmp
            if ! apk update; then
              sed -i 's|https://|http://|g' /etc/apk/repositories
              apk update
              apk add --no-cache ca-certificates
              sed -i 's|http://|https://|g' /etc/apk/repositories
              apk update
            fi
            apk add --no-cache bash git github-cli nodejs npm python3 curl ca-certificates openssh-client tar gzip
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
