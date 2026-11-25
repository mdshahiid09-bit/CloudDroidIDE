package com.clouddroidide.android

import com.clouddroidide.android.core.crypto.KeystoreGenerator
import com.clouddroidide.android.core.crypto.KeystoreParams
import com.clouddroidide.android.utils.AppConfigEditor
import com.clouddroidide.android.utils.DiagnosticParser
import com.clouddroidide.android.workspace.DiagnosticSeverity
import com.clouddroidide.android.workspace.ProjectTemplateGenerator
import com.clouddroidide.android.workspace.ProjectTemplateType
import com.clouddroidide.android.workspace.ProjectWorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreWorkspaceAndCryptoTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `template generator creates valid compose project and app config editor updates it`() {
        val root = tempFolder.newFolder("projects")
        val projDir = ProjectTemplateGenerator.generateProject(
            projectsRoot = root,
            projectName = "DemoCompose",
            packageName = "com.demo.composeapp",
            templateType = ProjectTemplateType.KOTLIN_COMPOSE
        ).getOrThrow()

        assertTrue(File(projDir, ".github/workflows/build.yml").exists())
        assertTrue(File(projDir, "app/src/main/java/com/demo/composeapp/MainActivity.kt").exists())

        val repo = ProjectWorkspaceRepository(root)
        val scanned = repo.listProjects()
        assertEquals(1, scanned.size)
        assertEquals("com.demo.composeapp", scanned[0].packageId)

        val cfg = AppConfigEditor.readConfig(projDir).getOrThrow()
        assertEquals("com.demo.composeapp", cfg.applicationId)
        assertEquals(1, cfg.versionCode)
        assertEquals("1.0.0", cfg.versionName)
        assertEquals(26, cfg.minSdk)
        assertEquals(36, cfg.targetSdk)

        val updated = cfg.copy(
            applicationId = "com.demo.renamed",
            versionCode = 7,
            versionName = "2.1.0",
            minSdk = 28,
            targetSdk = 36
        )
        AppConfigEditor.writeConfig(updated).getOrThrow()

        val reloaded = AppConfigEditor.readConfig(projDir).getOrThrow()
        assertEquals("com.demo.renamed", reloaded.applicationId)
        assertEquals(7, reloaded.versionCode)
        assertEquals("2.1.0", reloaded.versionName)
        assertEquals(28, reloaded.minSdk)
    }

    @Test
    fun `diagnostic parser extracts kotlin and javac errors`() {
        val logs = """
            > Task :app:compileDebugKotlin FAILED
            e: file:///home/runner/work/app/src/main/java/com/demo/MainActivity.kt:42:17 Unresolved reference: foo
            w: file:///home/runner/work/app/src/main/java/com/demo/Helper.kt:10:5 Unused variable
            /home/runner/work/app/src/main/java/com/demo/Legacy.java:18: error: ';' expected
        """.trimIndent()

        val diags = DiagnosticParser.parse(logs)
        assertEquals(3, diags.size)
        assertEquals(DiagnosticSeverity.ERROR, diags[0].severity)
        assertEquals(42, diags[0].line)
        assertEquals(17, diags[0].column)
        assertEquals(DiagnosticSeverity.WARNING, diags[1].severity)
        assertEquals(18, diags[2].line)
    }

    @Test
    fun `keystore generator builds valid verifiable pkcs12 keystore`() {
        val ksDir = tempFolder.newFolder("ks")
        val info = KeystoreGenerator.generateKeystore(
            ksDir,
            KeystoreParams(
                fileName = "test_release.jks",
                alias = "releasekey",
                keystorePassword = "secret123",
                keyPassword = "secret123",
                validityYears = 10,
                commonName = "Test",
                organizationalUnit = "Eng",
                organization = "CloudDroid",
                locality = "BLR",
                state = "KA",
                country = "IN"
            )
        )
        assertTrue(info.file.exists())
        assertNotNull(info.sha256Fingerprint)
        val (alias, fp) = KeystoreGenerator.verifyKeystore(info.file.readBytes(), "secret123")
        assertEquals("releasekey", alias)
        assertEquals(info.sha256Fingerprint, fp)
    }
}
