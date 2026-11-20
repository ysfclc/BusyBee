# App Observer — recovery and acceptance record

## Product boundary
App Observer is a generic, read-only Android behaviour recorder for BusyBee engineering. It discovers launchable apps dynamically, lets the user select one target per manually started session, and retains structure-only UI evidence plus privacy-allowlisted network metadata. Its on-device viewer renders recent structural evidence and exact structural fingerprints; an explicit self-test target allows observation of App Observer itself. It is not BusyBee production code and does not replace the preserved App Observer Lab survey app.

## Source
- Repository: ysfclc/BusyBee
- Branch: fix/app-observer-capture-0.2.1
- Project: research/app-observer-capture/android
- Live Viewer debug package: com.ysfclc.appobserver.capture.generic.viewer.debug
- Version: 0.3.1-live-viewer-debug (versionCode 5)
- Launcher label: App Observer Live Viewer
- Package is intentionally distinct so it installs alongside earlier capture/survey test editions; GitHub debug signatures remain ephemeral.

## Recovery and product decisions
- Keep the original research/app-observer survey app untouched.
- Do not limit observations to a provider allowlist. Discover installed launcher apps with Android package visibility and filter in code to the manually selected target.
- Keep Accessibility read-only. Capture structural node metadata and source-node signatures, never displayed or typed text.
- Notification listener records envelope metadata only, never title/body/reply/action labels.
- PCAPdroid TCP packets are inspected in memory; persist only protocol/port/size/time metadata. Never persist raw addresses, payloads or PCAP.
- Disable full payload capture and TLS decryption unconditionally.
- Sanitise legacy JSONL fields again at export; never include a legacy network.pcap file in exported ZIPs.
- No action executor, request replay, third-party process instrumentation or app control is part of this recorder.

## Acceptance status
- Isolation from BusyBee production and original survey app: preserved by project/package boundary.
- Generic app discovery, live evidence rendering, exact structural fingerprinting and self-test package filtering: implemented in source.
- Unit tests and APK build: passed on 2026-11-20 in GitHub Actions run [44714525454](https://github.com/ysfclc/BusyBee/actions/runs/44714525454) (run #113, commit `38742a72d6e79c97f8cfab16b02ab3b6907f1012`). Unit tests, APK assembly, package identity verification, APK v2 signature verification and artifact upload all passed.
- Test artifact: artifact ID `14618502708` (`app-observer-live-viewer-113`); package `com.ysfclc.appobserver.capture.generic.viewer.debug`; version `0.3.1-live-viewer-debug`; APK SHA-256 `581456f1a934a3e1a814070531a912e108f30df0ce141dbd040dc10340a23e8d`; signer certificate SHA-256 `d67a01986f3c5cb0266a46fc505c62e80bb7159e69ef62a84e863a0e735b014c`.
- PCAPdroid interoperability on a physical Android device: not verified.
- Actual UI event coverage across a representative set of apps, including self-observation: not verified.
- Actual app behaviour classification: not implemented.
- Semantic UI/network correlation engine: not implemented.
- Reusable automation skills or action executor: not implemented.
- Real-device privacy acceptance across different OEMs/apps: not verified.

Do not call this app understanding or automation complete until controlled traces have been collected, classification repeatability measured, and physical-device tests pass.
