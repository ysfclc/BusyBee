# App Observer Capture — recovery and acceptance record

## Product boundary
App Observer Capture is a separate Android research recorder for BusyBee engineering. It pairs selected-app read-only UI observation with PCAPdroid's app-filtered packet capture. It is not BusyBee production code and does not replace the preserved App Observer Lab 0.1 survey app.

## Source
- Repository: ysfclc/BusyBee
- Branch: fix/app-observer-capture-0.2.1
- Project: research/app-observer-capture/android
- Debug package: com.ysfclc.appobserver.capture.debug
- Version: 0.2.1-capture (versionCode 3)

## Recovery decision
The prior PCAPdroid implementation was recovered from closed PR #18 / branch research/app-observer-observation-2026-11-19. It had a successful CI build and parser unit tests, but was never accepted on the physical target phone. It also reused the survey app's package ID and used UDP Exporter.

This isolated copy keeps the 0.1.0 survey app and its verified artifact untouched. It changes the capture package ID so both editions can coexist. The capture transport is being hardened to PCAPdroid's TCP Exporter. CI evidence is not physical-device acceptance.

## Acceptance status
- Source port and isolation: passed; PR #22 changes only the new `research/app-observer-capture/**` project and its dedicated workflow. The original `research/app-observer/**` survey app and BusyBee production sources are untouched.
- Unit tests and APK build: passed on 2026-11-20 in GitHub Actions run [44675444835](https://github.com/ysfclc/BusyBee/actions/runs/44675444835) (PR run #20). The latest job completed unit tests, APK assembly, package-ID verification, APK v2 signature verification and artifact upload.
- PCAPdroid interoperability on physical Android device: not verified.
- Actual Uber/Bolt traffic and UI correlation: not verified.
- TLS decryption: optional only; no bypass or success claim.
- Verified test APK: `com.ysfclc.appobserver.capture.debug`, version `0.2.1-capture-debug`, APK SHA-256 `2439fe531fd6ef2f2ceee8d94b3a70ed19d0d8cf30c6d31e80b95b00faffc762`, signer certificate SHA-256 `cb4bd277994e5800973f04b6245b6d5d9986d78df261d70cb54c74de49d1d2de`, artifact ID `14601932523` (`app-observer-capture-test-20`).
- Stable install/update signing: CI debug signing is ephemeral (the certificate fingerprint differed between successful builds); a stable update channel requires a persistent signing identity. Do not distribute these CI artifacts as repeatable updates.

## Device acceptance gate
1. Install alongside App Observer Lab 0.1 without uninstalling or clearing existing app data.
2. Start a selected-app-only session after the user approves PCAPdroid.
3. Receive a valid PCAP header and packet records over TCP Exporter.
4. Produce matching UI and network timestamps for a controlled app event.
5. Stop cleanly, export a readable ZIP, and open the PCAP in Wireshark.
6. Preserve session evidence until explicit deletion or app-data removal.