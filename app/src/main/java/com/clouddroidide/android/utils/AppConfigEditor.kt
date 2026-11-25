package com.clouddroidide.android.utils

import com.clouddroidide.android.workspace.AppGradleConfig
import com.clouddroidide.android.workspace.BuildDiagnostic
import com.clouddroidide.android.workspace.DiagnosticSeverity
import java.io.File

/**
 * Reads and safely writes applicationId, versionCode, versionName, minSdk, and targetSdk
 * in app/build.gradle or app/build.gradle.kts.
 */
object AppConfigEditor {
    private val appIdRegex = Regex("""(applicationId\s*(?:=\s*|\s+)["'])([^"']+)(["'])""")
    private val versionCodeRegex = Regex("""(versionCode\s*(?:=\s*|\s+))(\d+)""")
    private val versionNameRegex = Regex("""(versionName\s*(?:=\s*|\s+)["'])([^"']+)(["'])""")
    private val minSdkRegex = Regex("""(minSdk(?:Version)?\s*(?:=\s*|\s+))(\d+)""")
    private val targetSdkRegex = Regex("""(targetSdk(?:Version)?\s*(?:=\s*|\s+))(\d+)""")

    fun findAppGradleFile(projectDir: File): File? {
        val kts = File(projectDir, "app/build.gradle.kts")
        if (kts.exists()) return kts
        val groovy = File(projectDir, "app/build.gradle")
        if (groovy.exists()) return groovy
        return null
    }

    fun readConfig(projectDir: File): Result<AppGradleConfig> = runCatching {
        val file = findAppGradleFile(projectDir)
            ?: error("No app/build.gradle(.kts) found in ${projectDir.name}")
        val text = file.readText()
        AppGradleConfig(
            gradleFile = file,
            applicationId = appIdRegex.find(text)?.groupValues?.getOrNull(2) ?: "",
            versionCode = versionCodeRegex.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1,
            versionName = versionNameRegex.find(text)?.groupValues?.getOrNull(2) ?: "1.0.0",
            minSdk = minSdkRegex.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 26,
            targetSdk = targetSdkRegex.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 36
        )
    }

    fun writeConfig(updated: AppGradleConfig): Result<Unit> = runCatching {
        require(updated.applicationId.isNotBlank()) { "applicationId cannot be blank" }
        require(updated.versionCode >= 1) { "versionCode must be >= 1" }
        require(updated.versionName.isNotBlank()) { "versionName cannot be blank" }
        require(updated.minSdk in 21..updated.targetSdk) { "minSdk must be <= targetSdk" }

        var text = updated.gradleFile.readText()
        text = replaceFirstGroup2(appIdRegex, text, updated.applicationId)
        text = replaceFirstGroup2(versionCodeRegex, text, updated.versionCode.toString())
        text = replaceFirstGroup2(versionNameRegex, text, updated.versionName)
        text = replaceFirstGroup2(minSdkRegex, text, updated.minSdk.toString())
        text = replaceFirstGroup2(targetSdkRegex, text, updated.targetSdk.toString())
        updated.gradleFile.writeText(text)
    }

    private fun replaceFirstGroup2(regex: Regex, input: String, newValue: String): String {
        val match = regex.find(input) ?: return input
        val g1 = match.groupValues[1]
        val g3 = if (match.groupValues.size > 3) match.groupValues[3] else ""
        return input.replaceRange(match.range, "$g1$newValue$g3")
    }
}

/**
 * Parses real Kotlin/Java/Gradle compiler logs into clickable Diagnostics.
 */
object DiagnosticParser {
    private val kotlinRegex = Regex("""^([ew]):\s+file://(/[^:\s]+):(\d+):(\d+)\s+(.+)$""")
    private val javacRegex = Regex("""^(/[^:\s]+\.(?:java|kt|kts|xml)):(\d+):\s+(error|warning):\s+(.+)$""")

    fun parse(logs: String): List<BuildDiagnostic> {
        val diagnostics = mutableListOf<BuildDiagnostic>()
        logs.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val ktMatch = kotlinRegex.find(line)
            if (ktMatch != null) {
                diagnostics += BuildDiagnostic(
                    filePath = ktMatch.groupValues[2],
                    line = ktMatch.groupValues[3].toIntOrNull() ?: 1,
                    column = ktMatch.groupValues[4].toIntOrNull() ?: 1,
                    severity = if (ktMatch.groupValues[1] == "e") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                    message = ktMatch.groupValues[5]
                )
                return@forEach
            }
            val javaMatch = javacRegex.find(line)
            if (javaMatch != null) {
                diagnostics += BuildDiagnostic(
                    filePath = javaMatch.groupValues[1],
                    line = javaMatch.groupValues[2].toIntOrNull() ?: 1,
                    column = 1,
                    severity = if (javaMatch.groupValues[3] == "error") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                    message = javaMatch.groupValues[4]
                )
            }
        }
        return diagnostics
    }
}
