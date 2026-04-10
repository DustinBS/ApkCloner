# AppCloner (Android GUI MVP)

This is a starter Android project for the AppCloner MVP. It contains a simple Kotlin app with two primary flows:

- Pick an installed app (lists user-installed apps).
- Pick an APK file (opens Storage Access Framework file picker).

Notes:
- Min SDK is set to 16 and compile/target SDK to 36 in the module Gradle file; adjust as needed.

Policy note: the app currently requests `QUERY_ALL_PACKAGES` in the manifest to support the installed-app picker during development. That permission is broad and may prevent Play Store publication. For production, prefer restricting package visibility via `<queries>` entries for known packages or implementing a picker that doesn't require `QUERY_ALL_PACKAGES`. See Android package visibility docs for migration guidance.
- This scaffold provides UI and placeholders for repackaging and installation logic. The actual repackaging/signing flows are TODO items.

To open and build:

1. Open this folder in Android Studio (`android-app`).
2. Let Android Studio sync Gradle and install any required SDK components.
3. Run on a device or emulator.

Next steps implemented by the agent:
- Scaffolding of project files and basic UI flows.

Desktop helper
--------------

Two helper scripts are included for desktop-based repackaging (Windows PowerShell):

- `repack.ps1` — Repackages an APK using `apktool`, `zipalign`, and `apksigner`.
- `pull_and_repack.ps1` — Pulls an installed app's APK from a connected device using `adb` and calls `repack.ps1`.

These tools are intended for trusted APKs (in-house or with explicit permission). They perform a best-effort package rename and label change, then rebuild and sign the APK.

Examples:

```powershell
# Repack a local APK
.\repack.ps1 -Input original.apk -Out cloned.apk -NewPackage com.example.app.clone -Label "App Clone" -Keystore mykeystore.jks -KeyAlias myalias -StorePass pass -KeyPass pass

# Pull installed package and repack
.\pull_and_repack.ps1 -PackageName com.example.app -Out cloned.apk -NewPackage com.example.app.clone -Label "App Clone" -Keystore mykeystore.jks -KeyAlias myalias -StorePass pass -KeyPass pass
```

Limitations:
- This script cannot bypass apps that require original signing keys or DRM protections.
- Some apps (split APKs, native libraries, sharedUserId) may fail after repackaging. Use the desktop helper only when appropriate.


Testing
-------

Run the included PowerShell helper to execute unit tests (it will try `gradlew` then `gradle`):

```powershell
cd android-app
.\run-tests.ps1
```

If the Gradle wrapper is not present and `gradle` is not installed, either open the `android-app` project in Android Studio and run tests there, or install Gradle and generate the wrapper locally with:

```powershell
gradle wrapper --gradle-version 8.2
```

If you'd like, I can add a Gradle wrapper to this repository for CI-style runs (this requires adding the wrapper files).

Additional developer notes (implemented by contributor agent)
--------------------------------------------------------

Dextools (smali/baksmali integration)
- A lightweight Java subproject `:dextools` builds a shaded, relocated JAR (`dextools-shaded-1.0-all.jar`) that embeds `org.smali`/`baksmali` classes. The app depends on that shaded artifact via a local file dependency to avoid Android transform conflicts.
- In the app, `RepackDexHelper` calls a bridge class reflectively to disassemble/reassemble dex files. A test-only stub bridge (`TestSmaliBridge`) is provided for unit tests so the CI run does not require native tooling.

Unit tests and test harness
- Unit tests were added and made self-contained; tests create temporary APK ZIP files and exercise `RepackHelper`, `RepackDexHelper`, and workspace detection logic. Run unit tests with Gradle:

```powershell
cd "$(pwd)"
./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Integration / emulator validation (recommended)
- The repository now includes a prototype on-device deep-unpack + auto-fix flow. To validate an end-to-end deep clone on an emulator or device, follow the steps in `docs/integration.md` which explain how to build, install, run the UI flow, and verify cloned APK behavior. For CI, create an instrumentation test or use `adb` to install and run smoke checks on an emulator image.

Security & audit notes
- The included embedded tooling (`:dextools` shaded JAR) currently depends only on `org.smali:smali`. The shaded JAR relocates packages to avoid classpath conflicts and excludes known annotation artifacts that can conflict with Android toolchain classes. See `docs/embedded_tools_audit.md` for a short audit checklist and mitigation notes (safety, licensing, and runtime surface).

Next recommended steps
- Run the integration validation on an emulator and exercise several real APKs (split APKs, apps using Firebase, apps with native libs) to collect and fix edge cases.
- Harden the RepackHelper production path: gate test-only fallbacks behind a `debug` flag, and remove `TestSmaliBridge` from production builds.
 - Harden the RepackHelper production path: test-only fallbacks have been moved into debug/test-only helpers (`DebugFallbacks`) invoked reflectively; `TestSmaliBridge` remains in `src/test` and will not be packaged in release builds.
- Add CI steps to run unit tests and (optionally) instrumentation tests in an emulator job.

