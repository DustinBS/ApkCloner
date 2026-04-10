# Embedded Tools Audit

This brief audit documents the embedded runtime tooling shipped or referenced by AppCloner and suggested mitigations.

Scope
- `:dextools` shaded JAR (`dextools-shaded-1.0-all.jar`) produced by the Shadow plugin.
- Runtime bridge usage (reflective calls) from `RepackDexHelper`.

Findings
- Dependencies: the `:dextools` module depends only on `org.smali:smali`. That is the only non-Android-tooling third-party library embedded in the shaded artifact.
- Shading/relocation: The Shadow plugin relocates `org.smali`, `org.jf`, and `org.antlr` namespaces into `com.example.appcloner.vendor.*` to avoid android transform/classpath conflicts.
- Exclusions: `com.google.errorprone` annotations are excluded to avoid duplicate-class issues with Android toolchain.
- Test stub: `TestSmaliBridge` is only included in `src/test` and should not be packaged into release builds.

Additional notes:
- Debug-only fallbacks (e.g., simple copy+sign used by unit tests) have been moved into the `debug` source set and are invoked reflectively from `RepackHelper` so that release builds do not include test-only signing logic.
- Signing-related helper functions were extracted to `SigningUtils` to reduce duplication and make the fallback implementation reuse the same signing logic in debug builds.

Risks
- Size: shading smali increases app size; keep the shaded jar minimal and consider shipping `dextools` as an optional, off-device artifact if size is a concern.
- Licensing: confirm `org.smali` license (Apache 2.0) and include notices if redistributing the library inside the app.
- Execution surface: disassembly/reassembly is CPU- and I/O-heavy and may time out or OOM on low-memory devices.
- Security: avoid executing arbitrary code from unpacked workspace; only perform textual replacements the app author can reason about.

Recommendations
1. Gate runtime dex remapping behind a user opt-in or a device capability check (RAM, disk space).
2. Strip debug-only components from release builds (ensure `TestSmaliBridge` is test-scope only and not included in release packaging).
3. Keep the shaded JAR minimal: consider using ProGuard/R8 to remove unused classes in the dextools artifact prior to shading.
4. Add a license/third-party notices file in `assets/THIRD_PARTY_NOTICES.txt` referencing `smali` license and any other bundled libs.
5. Add telemetry/logging (opt-in) to record when dex remapping runs and whether it succeeded/fails to prioritize fixes.
6. For maximum safety, prefer desktop repack for large or risky apps; provide clear UI guidance when on-device operations are partial.

Audit conclusion
- Current configuration is acceptable for experimental on-device dex remapping when gated behind opt-in; follow the recommendations above before enabling for broad user audiences.
