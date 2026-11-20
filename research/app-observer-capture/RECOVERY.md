# App Observer — recovery and acceptance record

## Product boundary
App Observer is a generic, read-only Android behaviour recorder for BusyBee engineering. It discovers launchable apps dynamically, lets the user select one target per manually started session, and retains structure-only UI evidence plus privacy-allowlisted network metadata. It is not BusyBee production code and does not replace the preserved App Observer Lab survey app.

## Source
- Repository: ysfclc/BusyBee
- Branch: fix/app-observer-capture-0.2.1
- Project: research/app-observer-capture/android
- Generic debug package: com.ysfclc.appobserver.capture.generic.debug
- Version: 0.3.0-generic-privacy (versionCode 4)
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
- Generic app discovery and privacy-first implementation: source changes committed; CI and runtime verification pending.
- Earlier build evidence: PR #22 run 20 passed tests, APK build, package-ID verification, APK signature verification and artifact upload for an earlier source revision only.
- Latest diagnostic revision before this generic-observer work failed compilation at MainActivity.kt:414; the current rewrite corrects that syntax issue, but a fresh CI pass is required.
- PCAPdroid interoperability on a physical Android device: not verified.
- Actual app behaviour classification: not implemented.
- Reusable automation execution: not implemented.
- Real-device privacy acceptance across different OEMs/apps: not verified.

Do not call this app understanding or automation complete until controlled traces have been collected, classification repeatability measured, and physical-device tests pass.
