# App Observer Lab — preserved working baseline

## Product identity

This is the original **App Observer Lab 0.1.0 survey build**. It is a standalone Android capability survey, separate from BusyBee. Its working scope is deliberately limited to public Android observation surfaces and on-device export.

This baseline is preserved as its own branch. Do not replace it with the later PCAPdroid evidence-recorder experiment. That is a separate, unverified-on-device research variant, not an upgrade or continuation of this app.

## Source of truth

- Repository: `ysfclc/BusyBee`
- Preserved source branch: `research/app-observer-lab-2026-11-16`
- Source commit: `6a8ac7fa9d1aaf41e6df6e8a51e2d6ec0e1571b1`
- App source: `research/app-observer/android`
- Package: `com.ysfclc.appobserver.debug`
- Version: `0.1.0-survey-debug` (versionCode 1)
- SDK: min 26, target 35

## Verified working build — recovered 2026-11-20

- GitHub Actions run: [44424379275](https://github.com/ysfclc/BusyBee/actions/runs/44424379275) — success.
- Unit tests and APK assembly: passed.
- APK v2 signature verification: passed.
- Artifact ID: `14483645939`.
- Original APK SHA-256: `5b4c903f53c0312b0412b1754bdce2335a00c0fab2098a2978300b3f9cc2ae57`.
- The artifact was downloaded again on 2026-11-20. Its APK hash was independently recomputed and matched the workflow checksum exactly.

The recovered file is the exact prior artifact, not a rebuild or a newly signed substitute.

## What this baseline does

- Installed app identity, version, target SDK and selected declared/granted permissions.
- Android Usage Access summaries for the previous 24 hours.
- Notification metadata after explicit Notification Access: counts, category, action count, ongoing state and MessagingStyle group-conversation boolean. It does not read title/body, sender, conversation/group names or message contents.
- Accessibility event counts and aggregate window-tree shape after explicit opt-in. It counts visible/clickable/editable/scrollable/password nodes but does not read node text, content descriptions or view IDs and performs no actions.
- Device-level active network transports and whether Android reports a VPN transport. It does not capture packets or per-app flows.
- On-device counters and JSON report export using Android's document picker.

The app has no INTERNET permission, telemetry, account login or automatic upload.

## What it does not do

No HTTPS decryption, TLS key capture, provider database access, private endpoint replay, provider-process instrumentation, packet capture or third-party app actions.

## Rebuild

From repository root:

```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer/android --no-daemon testDebugUnitTest assembleDebug
```

APK output:

`research/app-observer/android/app/build/outputs/apk/debug/app-debug.apk`

## Phone runbook

1. Install App Observer Lab.
2. Grant Usage Access, Notification Access and (optionally) the explicitly described read-only Accessibility service.
3. Return to the app and refresh.
4. Open Uber Driver, Bolt Driver, Waze, Google Maps and WhatsApp normally. Use them as usual for a while; no special interaction is needed.
5. Return and export the JSON report. Review it before sharing it back.

The report contains app/version metadata and aggregate counters only—not notification or screen content.

## Preservation rule

Keep this source branch and its verified APK/checksum as the known-good baseline. Any broader PCAPdroid, packet-capture or protocol-research build must live in a separate branch and version, and must never overwrite this baseline. A replacement is not accepted until the user explicitly chooses it and the new build passes its own phone acceptance test.
