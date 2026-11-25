package com.clouddroidide.android.workspace

import java.io.File

/**
 * Generates real, immediately compilable Android projects and CI/CD workflows.
 */
object ProjectTemplateGenerator {

    fun generateProject(
        projectsRoot: File,
        projectName: String,
        packageName: String,
        templateType: ProjectTemplateType
    ): Result<File> = runCatching {
        val cleanName = projectName.trim().replace(Regex("[^A-Za-z0-9_-]"), "")
        require(cleanName.isNotBlank()) { "Project name is invalid" }
        require(packageName.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
            "Package name must look like com.example.myapp"
        }

        val root = File(projectsRoot, cleanName)
        require(!root.exists()) { "Project '$cleanName' already exists" }
        root.mkdirs()

        val pkgPath = packageName.replace('.', '/')
        val appDir = File(root, "app")
        val srcMain = File(appDir, "src/main")
        val codeDir = File(srcMain, "java/$pkgPath").apply { mkdirs() }
        val resValues = File(srcMain, "res/values").apply { mkdirs() }
        val resLayout = File(srcMain, "res/layout").apply { mkdirs() }
        val gradleWrapperDir = File(root, "gradle/wrapper").apply { mkdirs() }

        File(root, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$cleanName"
            include(":app")
            """.trimIndent() + "\n"
        )

        File(root, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """.trimIndent() + "\n"
        )

        File(gradleWrapperDir, "gradle-wrapper.properties").writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent() + "\n"
        )

        File(root, ".gitignore").writeText(
            ".gradle/\nbuild/\napp/build/\nlocal.properties\n*.jks\n*.keystore\n*.apk\n*.aab\n"
        )

        File(appDir, "proguard-rules.pro").writeText("# Add project specific ProGuard rules here.\n")

        File(resValues, "strings.xml").writeText(
            """
            <resources>
                <string name="app_name">$cleanName</string>
            </resources>
            """.trimIndent() + "\n"
        )

        File(srcMain, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.$cleanName">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent() + "\n"
        )

        when (templateType) {
            ProjectTemplateType.KOTLIN_COMPOSE -> writeKotlinComposeTemplate(
                root, appDir, codeDir, resValues, cleanName, packageName
            )
            ProjectTemplateType.KOTLIN_XML -> writeKotlinXmlTemplate(
                root, appDir, codeDir, resValues, resLayout, cleanName, packageName
            )
            ProjectTemplateType.JAVA_XML -> writeJavaXmlTemplate(
                root, appDir, codeDir, resValues, resLayout, cleanName, packageName
            )
        }

        writeWorkflowFile(root, packageName, cleanName)
        root
    }

    private fun writeKotlinComposeTemplate(
        root: File,
        appDir: File,
        codeDir: File,
        resValues: File,
        cleanName: String,
        packageName: String
    ) {
        File(root, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
            }
            """.trimIndent() + "\n"
        )

        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "$packageName"
                compileSdk = 36

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0.0"
                }

                signingConfigs {
                    create("release") {
                        val ks = System.getenv("KEYSTORE_PATH")
                        if (!ks.isNullOrBlank()) storeFile = file(ks)
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEY_ALIAS") ?: ""
                        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        val hasKs = !System.getenv("KEYSTORE_PATH").isNullOrBlank()
                        if (hasKs) signingConfig = signingConfigs.getByName("release")
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
                buildFeatures { compose = true }
            }

            dependencies {
                implementation(platform("androidx.compose:compose-bom:2025.02.00"))
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.activity:activity-compose:1.10.0")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
            }
            """.trimIndent() + "\n"
        )

        File(resValues, "themes.xml").writeText(
            """
            <resources>
                <style name="Theme.$cleanName" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """.trimIndent() + "\n"
        )

        File(codeDir, "MainActivity.kt").writeText(
            """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Button
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                var count by remember { mutableIntStateOf(0) }
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$cleanName", style = MaterialTheme.typography.headlineMedium)
                                    Text("Tap count: ${'$'}count", modifier = Modifier.padding(vertical = 12.dp))
                                    Button(onClick = { count++ }) {
                                        Text("Increment")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeKotlinXmlTemplate(
        root: File,
        appDir: File,
        codeDir: File,
        resValues: File,
        resLayout: File,
        cleanName: String,
        packageName: String
    ) {
        File(root, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            }
            """.trimIndent() + "\n"
        )

        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "$packageName"
                compileSdk = 36

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0.0"
                }

                signingConfigs {
                    create("release") {
                        val ks = System.getenv("KEYSTORE_PATH")
                        if (!ks.isNullOrBlank()) storeFile = file(ks)
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEY_ALIAS") ?: ""
                        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        val hasKs = !System.getenv("KEYSTORE_PATH").isNullOrBlank()
                        if (hasKs) signingConfig = signingConfigs.getByName("release")
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
                buildFeatures { viewBinding = true }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0")
            }
            """.trimIndent() + "\n"
        )

        writeXmlThemesAndLayout(resValues, resLayout, cleanName)

        File(codeDir, "MainActivity.kt").writeText(
            """
            package $packageName

            import android.os.Bundle
            import android.widget.Button
            import android.widget.TextView
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
                private var count = 0

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    val titleText = findViewById<TextView>(R.id.tvTitle)
                    val actionBtn = findViewById<Button>(R.id.btnAction)
                    actionBtn.setOnClickListener {
                        count++
                        titleText.text = "$cleanName: ${'$'}count"
                    }
                }
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeJavaXmlTemplate(
        root: File,
        appDir: File,
        codeDir: File,
        resValues: File,
        resLayout: File,
        cleanName: String,
        packageName: String
    ) {
        File(root, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
            }
            """.trimIndent() + "\n"
        )

        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
            }

            android {
                namespace = "$packageName"
                compileSdk = 36

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0.0"
                }

                signingConfigs {
                    create("release") {
                        val ks = System.getenv("KEYSTORE_PATH")
                        if (!ks.isNullOrBlank()) storeFile = file(ks)
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEY_ALIAS") ?: ""
                        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        val hasKs = !System.getenv("KEYSTORE_PATH").isNullOrBlank()
                        if (hasKs) signingConfig = signingConfigs.getByName("release")
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            dependencies {
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0")
            }
            """.trimIndent() + "\n"
        )

        writeXmlThemesAndLayout(resValues, resLayout, cleanName)

        File(codeDir, "MainActivity.java").writeText(
            """
            package $packageName;

            import android.os.Bundle;
            import android.widget.Button;
            import android.widget.TextView;
            import androidx.appcompat.app.AppCompatActivity;

            public class MainActivity extends AppCompatActivity {
                private int count = 0;

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_main);
                    TextView titleText = findViewById(R.id.tvTitle);
                    Button actionBtn = findViewById(R.id.btnAction);
                    actionBtn.setOnClickListener(v -> {
                        count++;
                        titleText.setText("$cleanName: " + count);
                    });
                }
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeXmlThemesAndLayout(resValues: File, resLayout: File, cleanName: String) {
        File(resValues, "themes.xml").writeText(
            """
            <resources>
                <style name="Theme.$cleanName" parent="Theme.Material3.DayNight.NoActionBar" />
            </resources>
            """.trimIndent() + "\n"
        )

        File(resLayout, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="24dp">

                <TextView
                    android:id="@+id/tvTitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="$cleanName"
                    android:textSize="22sp" />

                <Button
                    android:id="@+id/btnAction"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="Tap Me" />
            </LinearLayout>
            """.trimIndent() + "\n"
        )
    }

    fun writeWorkflowFile(projectRoot: File, packageName: String, projectName: String): File {
        val workflowDir = File(projectRoot, ".github/workflows").apply { mkdirs() }
        val file = File(workflowDir, "build.yml")
        file.writeText(
            """
            name: Android Cloud Build

            on:
              push:
                branches: [ main ]
              workflow_dispatch:
                inputs:
                  play_track:
                    description: 'Google Play track (internal, alpha, beta, production)'
                    required: false
                    default: ''

            jobs:
              build:
                runs-on: ubuntu-latest

                steps:
                  - uses: actions/checkout@v4

                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: 17

                  - name: Set up Android SDK
                    uses: android-actions/setup-android@v3

                  - name: Ensure Gradle wrapper
                    uses: gradle/actions/setup-gradle@v4
                    with:
                      gradle-version: '8.9'

                  - name: Build Debug APK
                    run: gradle assembleDebug --stacktrace

                  - name: Upload Debug APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: ${projectName}-debug-apk
                      path: app/build/outputs/apk/debug/*.apk
                      if-no-files-found: error

                  - name: Check Signing Secrets
                    id: signing
                    env:
                      KEYSTORE_BASE64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
                      KEYSTORE_PASSWORD: ${'$'}{{ secrets.KEYSTORE_PASSWORD }}
                      KEY_ALIAS: ${'$'}{{ secrets.KEY_ALIAS }}
                      KEY_PASSWORD: ${'$'}{{ secrets.KEY_PASSWORD }}
                    run: |
                      if [ -n "${'$'}KEYSTORE_BASE64" ] && [ -n "${'$'}KEYSTORE_PASSWORD" ] && [ -n "${'$'}KEY_ALIAS" ] && [ -n "${'$'}KEY_PASSWORD" ]; then
                        echo "enabled=true" >> "${'$'}GITHUB_OUTPUT"
                      else
                        echo "enabled=false" >> "${'$'}GITHUB_OUTPUT"
                      fi

                  - name: Decode Keystore
                    if: steps.signing.outputs.enabled == 'true'
                    env:
                      KEYSTORE_BASE64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
                    run: printf '%s' "${'$'}KEYSTORE_BASE64" | base64 --decode > release.jks

                  - name: Build Signed Release APK & AAB
                    if: steps.signing.outputs.enabled == 'true'
                    env:
                      KEYSTORE_PATH: ${'$'}{{ github.workspace }}/release.jks
                      KEYSTORE_PASSWORD: ${'$'}{{ secrets.KEYSTORE_PASSWORD }}
                      KEY_ALIAS: ${'$'}{{ secrets.KEY_ALIAS }}
                      KEY_PASSWORD: ${'$'}{{ secrets.KEY_PASSWORD }}
                    run: gradle assembleRelease bundleRelease --stacktrace

                  - name: Clean Keystore
                    if: always()
                    run: rm -f release.jks

                  - name: Upload Signed Release Artifacts
                    if: steps.signing.outputs.enabled == 'true'
                    uses: actions/upload-artifact@v4
                    with:
                      name: ${projectName}-release-artifacts
                      path: |
                        app/build/outputs/apk/release/*.apk
                        app/build/outputs/bundle/release/*.aab

                  - name: Upload to Google Play
                    if: steps.signing.outputs.enabled == 'true' && github.event.inputs.play_track != '' && secrets.PLAY_STORE_SERVICE_ACCOUNT_JSON != ''
                    uses: r0adkll/upload-google-play@v1
                    with:
                      serviceAccountJsonPlainText: ${'$'}{{ secrets.PLAY_STORE_SERVICE_ACCOUNT_JSON }}
                      packageName: $packageName
                      releaseFiles: app/build/outputs/bundle/release/*.aab
                      track: ${'$'}{{ github.event.inputs.play_track }}
                      status: completed
            """.trimIndent() + "\n"
        )
        return file
    }
}
