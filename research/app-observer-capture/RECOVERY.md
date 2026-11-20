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
- Source port and isolation: in progress until PR and CI checks pass.
- Unit tests and APK build: pending for this branch.
- PCAPdroid interoperability on physical Android device: not verified.
- Actual Uber/Bolt traffic and UI correlation: not verified.
- TLS decryption: optional only; no bypass or success claim.
- Stable install/update signing: CI debug signing is ephemeral; a stable user-facing update requires a persistent signing identity.

## Device acceptance gate
1. Install alongside App Observer Lab 0.1 without uninstalling or clearing existing app data.
2. Start a selected-app-only session after the user approves PCAPdroid.
3. Receive a valid PCAP header and packet records over TCP Exporter.
4. Produce matching UI and network timestamps for a controlled app event.
5. Stop cleanly, export a readable ZIP, and open the PCAP in Wireshark.
6. Preserve session evidence until explicit deletion or app-data removal.