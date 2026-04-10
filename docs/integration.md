# Integration / Emulator Validation

This document describes steps to validate the deep-unpack and repack flow on an Android emulator or device.

Prerequisites
- Android SDK and emulator images installed (API 24+ recommended to match `minSdkVersion`)
- `adb` available on PATH
- Gradle wrapper: use `./gradlew.bat` on Windows

Local manual validation
1. Build the app and the shaded dextools jar:

```powershell
# Build dextools shaded JAR and app
./gradlew.bat :dextools:shadowJar :app:assembleDebug
```

2. Install the debug app on an emulator or device:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

3. Open the app on the device/emulator and use the UI:
- Select an installed app to clone, or pick an APK via the picker.
- Ensure `Deep Unpack` and `Auto-fix` are enabled in the UI.
- Start a clone job and observe the WorkManager progress and any warnings.

4. Verify output:
- If `Install Immediately` is enabled, the app will prompt to install the cloned APK.
- Otherwise, retrieve the cloned APK (or desktop bundle) from the app output folder or configured destination URI.

Automated emulator smoke test (manual setup)
- Add an instrumentation test that installs a test APK and triggers the UI/worker to run a repack flow, then assert the cloned APK exists and installs.
- For CI, use a cloud device or Android Emulator in headless mode. Use `adb` to push test APKs and run instrumentation tests.

Troubleshooting
- If repack fails due to parsing errors (binary manifest issues), the code includes a test fallback that will sign a copied APK in debug/test flows; this fallback should be removed or gated before production use.
- If dex remapping fails on-device, prefer the desktop repack path (use `prepare desktop bundle` in UI), then run `repack.ps1` on a PC.

Notes
- The on-device dex remapping is experimental and heavy; prefer desktop repack for complex apps. Use the audit guidance in `docs/embedded_tools_audit.md` before enabling dex remapping for end users.
