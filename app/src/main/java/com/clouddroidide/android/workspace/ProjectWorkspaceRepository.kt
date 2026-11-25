package com.clouddroidide.android.workspace

import java.io.File

/**
 * Scans local project directories, parses Gradle applicationId and active Git branch,
 * and performs real File & Directory CRUD operations.
 */
class ProjectWorkspaceRepository(val projectsRoot: File) {
    init {
        if (!projectsRoot.exists()) projectsRoot.mkdirs()
    }

    fun listProjects(): List<AndroidProject> {
        val dirs = projectsRoot.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?: return emptyList()

        return dirs.map { dir ->
            AndroidProject(
                name = dir.name,
                directory = dir,
                packageId = parsePackageId(dir),
                gitBranch = parseGitBranch(dir),
                lastModifiedMillis = dir.lastModified(),
                hasWorkflow = File(dir, ".github/workflows/build.yml").exists() ||
                    File(dir, ".github/workflows/build.yaml").exists()
            )
        }.sortedByDescending { it.lastModifiedMillis }
    }

    fun parsePackageId(projectDir: File): String {
        val candidates = listOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle"),
            File(projectDir, "build.gradle.kts"),
            File(projectDir, "build.gradle")
        )
        val regex = Regex("""applicationId\s*=?\s*["']([^"']+)["']""")
        val nsRegex = Regex("""namespace\s*=?\s*["']([^"']+)["']""")
        for (file in candidates) {
            if (!file.exists()) continue
            val text = file.readText()
            regex.find(text)?.groupValues?.getOrNull(1)?.let { return it }
            nsRegex.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return "unconfigured"
    }

    fun parseGitBranch(projectDir: File): String {
        val headFile = File(projectDir, ".git/HEAD")
        if (!headFile.exists()) return "no-git"
        val head = headFile.readText().trim()
        return when {
            head.startsWith("ref: refs/heads/") -> head.removePrefix("ref: refs/heads/")
            head.length >= 7 -> head.take(7)
            else -> "HEAD"
        }
    }

    fun parseGitRemoteOwnerRepo(projectDir: File): Pair<String, String>? {
        val configFile = File(projectDir, ".git/config")
        if (!configFile.exists()) return null
        val text = configFile.readText()
        val match = Regex("""url\s*=\s*.*github\.com[:/]([^/\s]+)/([^\s/.]+)(?:\.git)?""").find(text)
            ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    fun listFileTree(root: File, expandedDirs: Set<String>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: return
            for (child in children) {
                if (child.name == ".git" || child.name == ".gradle" || child.name == "build") continue
                result += FileNode(child, child.name, child.isDirectory, depth)
                if (child.isDirectory && expandedDirs.contains(child.absolutePath)) {
                    walk(child, depth + 1)
                }
            }
        }
        walk(root, 0)
        return result
    }

    fun createFile(parentDir: File, name: String): Result<File> = runCatching {
        require(name.isNotBlank() && !name.contains('/')) { "Invalid file name" }
        val file = File(parentDir, name.trim())
        require(!file.exists()) { "File already exists" }
        file.parentFile?.mkdirs()
        file.createNewFile()
        file
    }

    fun createFolder(parentDir: File, name: String): Result<File> = runCatching {
        require(name.isNotBlank() && !name.contains('/')) { "Invalid folder name" }
        val dir = File(parentDir, name.trim())
        require(!dir.exists()) { "Folder already exists" }
        require(dir.mkdirs()) { "Unable to create folder" }
        dir
    }

    fun renameNode(target: File, newName: String): Result<File> = runCatching {
        require(newName.isNotBlank() && !newName.contains('/')) { "Invalid name" }
        val dest = File(target.parentFile, newName.trim())
        require(!dest.exists()) { "Target already exists" }
        require(target.renameTo(dest)) { "Rename failed" }
        dest
    }

    fun deleteNode(target: File): Result<Unit> = runCatching {
        require(target.deleteRecursively()) { "Failed to delete ${target.name}" }
    }
}
