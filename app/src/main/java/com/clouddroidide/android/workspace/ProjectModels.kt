package com.clouddroidide.android.workspace

import java.io.File

enum class ProjectTemplateType(val label: String, val subtitle: String) {
    KOTLIN_COMPOSE("Kotlin + Jetpack Compose", "Material 3 Dark, Kotlin 2.0.21, AGP 8.7.3"),
    KOTLIN_XML("Kotlin + XML Views", "AppCompat + Material Components, ViewBinding"),
    JAVA_XML("Java + XML Views", "Java 17 + AppCompat + Material Components")
}

data class AndroidProject(
    val name: String,
    val directory: File,
    val packageId: String,
    val gitBranch: String,
    val lastModifiedMillis: Long,
    val hasWorkflow: Boolean
)

data class FileNode(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int
)

data class OpenFileTab(
    val file: File,
    val content: String,
    val isModified: Boolean = false
)

data class AppGradleConfig(
    val gradleFile: File,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int
)

data class BuildDiagnostic(
    val filePath: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val message: String
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING
}
