# CloudDroid IDE (`com.clouddroidide.android`) — Technical Specification

## 1. Product Summary
**CloudDroid IDE** is a zero-backend, production-grade Android mobile IDE and DevOps companion targeting `arm64-v8a` (`aarch64`) Android devices (`minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`). It combines a full Sora-based multi-tab code editor, an embedded native AArch64 PTY Linux container (Alpine + PRoot + configurable `agy` CLI), a GitHub REST v3 / Actions cloud build engine, resumable Foreground Service download manager, forced GitHub Releases auto-updater, in-app JKS/PKCS12 keystore generation, Libsodium SealedBox GitHub Secrets encryption, Play Store publishing workflow orchestration, and APK/AAB inspection utilities.

## 2. User Roles & Permissions
- **Single Local Developer Role:** Zero-backend local-first architecture. All credentials (GitHub PAT / OAuth access token, keystore passwords, service account JSON references) reside exclusively on-device inside Android `EncryptedSharedPreferences` (AES-256-GCM / AES-256-SIV).
- **GitHub Permissions:** Determined by the authenticated user's PAT or OAuth scope (`repo`, `workflow`, `write:packages` / `read:user`).

## 3. Assumptions (Confirmed Defaults)
1. **Default Auto-Update Repository:** `mdshahiid09-bit/CloudDroidIDE` in `AppConfig.kt`, editable at runtime in Settings.
2. **AArch64 Linux Environment:** Official Alpine Linux `3.24.4` AArch64 minirootfs (`alpine-minirootfs-3.24.4-aarch64.tar.gz`) + Alpine `proot-static` (`5.4.0-r2` `aarch64`, also bundled as `libproot.so` in `jniLibs/arm64-v8a` for Android 10+ W^X `targetSdk = 36` execution compatibility). Bootstraps `bash`, `git`, `github-cli` (`gh`), `nodejs`, `npm`, `python3`, `curl`, `ca-certificates`, and `openssh-client`.
3. **Antigravity CLI (`agy`) Source:** Configurable in Environment Setup UI and Settings (`AppConfig` + DataStore override). No dummy or hardcoded URL is invented; if blank, Environment Setup guides the user to enter their `agy` GitHub release tarball or install script URL / npm package before running the `agy` installer step.
4. **GitHub Authentication:** Personal Access Token (PAT) works immediately out-of-the-box. GitHub Device Flow OAuth is fully implemented and reads `GITHUB_OAUTH_CLIENT_ID` from `BuildConfig` (supplied via `gradle.properties` / `local.properties`) or runtime Settings.
5. **Native PTY Bridge:** Precompiled `arm64-v8a` JNI shared library (`libclouddroid_pty.so` compiled from `cpp/pty_bridge.cpp` using `openpty`, `fork`, `execve`, `TIOCSWINSZ`) plus CMake source for standard NDK builds.
6. **Zero-Emoji Policy:** All UI visuals use Material Icons Extended and custom Compose/VectorDrawable icons; zero Unicode emojis are used in UI strings.

## 4. Core Feature Modules & Acceptance Criteria
- **Module A (`AppConfig` & Settings):** Centralized configuration with DataStore-backed overrides for update repo (`owner/name`), rootfs URL, PRoot URL, `agy` install URL/command, and GitHub OAuth Client ID.
- **Module B (`DownloadForegroundService`):** OkHttp streaming downloader supporting HTTP `Range` resume, real-time speed (MB/s), percentage, pause/resume/cancel actions, and live `NotificationCompat` progress notifications (`FOREGROUND_SERVICE_DATA_SYNC`). Used for rootfs bootstrap, forced APK updates, and GitHub Actions artifacts.
- **Module C (Forced Auto-Updater):** Queries `GET /repos/{owner}/{repo}/releases/latest` on launch, compares semver tags against `BuildConfig.VERSION_NAME`, blocks dismissal via a full-screen forced modal when a newer release exists, downloads the `.apk` asset via `DownloadForegroundService`, verifies non-empty archive integrity, and triggers `FileProvider` package installation (`REQUEST_INSTALL_PACKAGES`).
- **Module D (AArch64 PRoot Linux & Embedded PTY):** Downloads & extracts Alpine rootfs into `filesDir/linux_env/rootfs`, runs setup (`apk add bash git github-cli nodejs npm python3 curl ca-certificates openssh-client`), exposes an interactive ANSI/VT terminal backed by `libclouddroid_pty.so` (`PtySession`), quick extra keys (`ESC`, `TAB`, `CTRL`, `^C`, arrows, `agy`), and `agy` setup/execution.
- **Module E (Projects, Templates & Git):**
  - Workspace scanner under `filesDir/projects` parsing real `applicationId` from `build.gradle(.kts)`, active `.git/HEAD` branch, and last modified timestamp.
  - Project Creation Wizard generating immediately compilable **Kotlin + Jetpack Compose**, **Kotlin + XML**, and **Java + XML** Android projects complete with Gradle wrapper scripts, manifests, resources, and `.github/workflows/build.yml`.
  - Git CLI/JGit-free real execution via system/PRoot `git` for clone (using stored GitHub token safely), `status`, `add`, `commit`, `push`, `pull`, and branch checkout/creation.
- **Module F (Cloud Build Engine):**
  - Generates/updates `.github/workflows/build.yml` (`assembleDebug`, `assembleRelease`, `bundleRelease`, optional Play Store `r0adkll/upload-google-play@v1`).
  - Triggers `workflow_dispatch`, polls runs & jobs in real time, downloads & parses real job logs, parses compiler diagnostics (`e: file:///...` / `error:`) into tap-to-jump IDE diagnostics, and downloads `.apk`/`.aab` artifacts for one-tap `FileProvider` installation.
- **Module G (Keystore, GitHub Secrets & Play Store):**
  - Real `PKCS12` / `JKS` keystore generation with BouncyCastle `X509v3CertificateBuilder` + RSA-2048 keypair (`CN`, `OU`, `O`, `L`, `ST`, `C`, validity years) and `.jks`/`.p12` file import.
  - Fetches repo Actions public key (`GET /repos/{owner}/{repo}/actions/secrets/public-key`), encrypts values using `LazySodiumAndroid` `cryptoBoxSeal` (Libsodium SealedBox), and uploads `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `PLAY_STORE_SERVICE_ACCOUNT_JSON`.
  - Play Store tab selects release track (`internal`, `alpha`, `beta`, `production`), triggers release + upload workflow, tracks job status, and downloads signed `.aab`.
- **Module H (Developer Utilities):**
  - **App Config Editor:** Safely parses and updates `applicationId`, `versionCode`, `versionName`, `minSdk`, and `targetSdk` in `app/build.gradle(.kts)`.
  - **APK / AAB Inspector:** Uses Android `PackageManager.getPackageArchiveInfo` + `ZipFile` / `MessageDigest` (`SHA-256`) to inspect file size, package name, version code/name, requested permissions, and signing certificate SHA-256 fingerprint.

## 5. Screens & Navigation Map
1. **Home Dashboard (`workspace`)**: Top bar (Logo + GitHub Auth status + Settings), inline environment/download banner, 2x2 Action Grid (`New Project`, `Open Project`, `Clone Repository`, `AArch64 Terminal`), real Recent Projects list with 3-dot overflow menu (`Open IDE`, `Cloud Build`, `Git Sync`, `Delete`).
2. **IDE Workspace (`ide/{projectId}`)**: Left File Explorer drawer (CRUD: New File, New Folder, Rename, Delete, Refresh), scrollable multi-file tab bar with unsaved dot, Sora Editor (`AndroidView` + TextMate highlighting for Kotlin/Java/XML/Gradle/YAML/JSON/Bash, undo/redo, search/replace), Quick Symbol Bar, collapsible bottom sheet (`Terminal`, `Build Logs`, `Logcat`, `Diagnostics`), plus modals for Git Sync, App Config Editor, and APK Inspector.
3. **Keystores & Secrets (`keystore`)**: Keystore generator/importer, saved keystores list, GitHub repo selector, one-tap SealedBox secret upload.
4. **Build History (`builds`)**: GitHub repository workflow runs, live step progress, real log viewer, artifact list with in-app download & install.
5. **Play Store (`playstore`)**: Service account JSON importer & `PLAY_STORE_SERVICE_ACCOUNT_JSON` uploader, track selector (`internal`, `alpha`, `beta`, `production`), release trigger & status, direct `.aab` download.
6. **Standalone Terminal / Environment Setup (`terminal`)** & **Settings (`settings`)** & **GitHub Auth Modal** & **Forced Update Overlay**.

## 6. Out of Scope
- Proprietary backend servers or cloud databases (100% zero-backend GitHub + local storage).
- On-device full Gradle Android APK compilation (offloaded to GitHub Actions Cloud Build; on-device Linux provides `git`, `gh`, `node`, `python`, `bash`, and `agy`).
