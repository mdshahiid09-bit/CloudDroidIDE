# CloudDroid IDE (`com.clouddroidide.android`)

Zero-backend Android mobile IDE, AArch64 Linux PRoot container, and GitHub Actions DevOps studio built with Kotlin, Jetpack Compose (Material 3 Dark), Sora Editor, OkHttp/Retrofit, BouncyCastle, and Libsodium.

## Features
- **Sora Code Editor + File Explorer:** TextMate syntax highlighting (Kotlin, Java, XML, Gradle, YAML, JSON, Bash, Python, JS, Markdown), multi-tab bar, undo/redo, find & replace, quick symbol bar, and file/folder CRUD.
- **Embedded AArch64 Linux PRoot Terminal:** Native `arm64-v8a` JNI PTY bridge (`libclouddroid_pty.so`) + Alpine AArch64 rootfs + PRoot (`libproot.so`) bootstrapping `bash`, `git`, `gh`, `nodejs`, `python3`, and user-configured `agy` CLI.
- **Unified Resumable Download Manager:** OkHttp HTTP `Range` streaming with `DownloadForegroundService` live progress notifications.
- **Forced GitHub Releases Auto-Updater:** Semver check on launch with non-dismissible update modal and `FileProvider` APK installer.
- **GitHub Auth & Git Sync:** PAT stored in `EncryptedSharedPreferences` + configurable GitHub Device Flow OAuth + in-container Git clone, stage, commit, push, pull, and branch management.
- **Cloud Build & Play Store Publishing:** Workflow generator, `workflow_dispatch` trigger, real job step/log viewer, clickable compiler diagnostics, `.apk`/`.aab` artifact download/install, in-app PKCS12/JKS keystore generation, Libsodium SealedBox secret encryption (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_SERVICE_ACCOUNT_JSON`), App Config Editor, and APK/AAB Inspector.

## Build & Test
```bash
./gradlew testDebugUnitTest assembleDebug
```
On Termux (`arm64-v8a`), pass the local `aapt2` override:
```bash
./gradlew testDebugUnitTest assembleDebug -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

## Optional Build-Time Config (`local.properties`)
```properties
sdk.dir=/path/to/android-sdk
GITHUB_OAUTH_CLIENT_ID=your_github_oauth_app_client_id
```

## CI/CD GitHub Secrets (`.github/workflows/build.yml`)
Configure these repository secrets for signed Release APK/AAB and automated GitHub Release / Play Store publishing:
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `PLAY_STORE_SERVICE_ACCOUNT_JSON` (optional, for Google Play uploads)
