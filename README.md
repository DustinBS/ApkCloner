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
