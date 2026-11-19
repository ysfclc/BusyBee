# App Observer Lab recovery record

This is a standalone diagnostic app. It is NOT BusyBee and must not be merged into BusyBee's product UI.

## Source of truth
- Source branch: `research/app-observer-lab-2026-11-16`
- Source commit at this recovery: `6a8ac7fa9d1aaf41e6df6e8a51e2d6ec0e1571b1`
- Pull request: #17 (draft)
- Package: `com.ysfclc.appobserver.debug`
- APK: `research/app-observer/android/app/build/outputs/apk/debug/app-debug.apk`

## Verified recovery APK
- GitHub Actions workflow run: `44424379275` (App Observer Lab APK, success)
- Artifact ID: `14483645939` (temporary; expires 2026-12-02)
- APK SHA-256: `5b4c903f53c0312b0412b1754bdce2335a00c0fab2098a2978300b3f9cc2ae57`

The checksum was verified against the APK extracted from the artifact. Artifact is temporary; source code and reproducible build instructions are the durable recovery route.

## Rebuild
From repository root:
```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer/android --no-daemon testDebugUnitTest assembleDebug
```

## Scope guard
App Observer is the place for observation experiments. Do not add App Observer experiments into BusyBee by default. Keep its existing scope and permissions explicit, and don't expand it into packet capture, TLS decryption, credential collection, or third-party app automation without a separate reviewed task.

## Recovery procedure
1. Read this file and `README.md`.
2. Build from the source branch above; do not rely on a temporary artifact.
3. Verify APK package, signature, and checksum.
4. Run the phone checklist in `README.md`.
5. Preserve the last verified APK checksum and build run here before replacing it.
