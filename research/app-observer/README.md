# App Observer Lab

A standalone Android diagnostic APK, separate from BusyBee's application ID and source code. It measures which public Android observation surfaces produce signals for a small, explicit set of installed apps.

Targets: Uber Driver (`com.ubercab.driver`), Bolt Driver (`ee.mtakso.driver`), Waze (`com.waze`), Google Maps (`com.google.android.apps.maps`), WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).

## What it measures

- Installed app identity, version, target SDK and selected declared/granted permissions.
- Android Usage Access summaries for the previous 24 hours.
- Notification metadata after explicit Notification Access: counts, category, action count, ongoing state and MessagingStyle group-conversation boolean. It never reads notification title/body, sender, conversation/group names or message contents.
- Accessibility event counts and aggregate window-tree shape after explicit opt-in. It counts visible/clickable/editable/scrollable/password nodes but never reads node text, content descriptions, view IDs or performs actions.
- Device-level active network transports and whether Android reports a VPN transport. It does not capture packets or per-app flows.

Counters stay on-device. The app has no INTERNET permission, telemetry, account login or automatic upload. The user can reset counters and export a JSON report with Android's document picker.

## What it does not do

No HTTPS decryption, TLS key capture, provider database access, private endpoint replay, provider-process instrumentation or third-party app actions. VPN transport detection is not VPN packet capture. The report states these limits explicitly.

## Build

From repository root:

```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer/android testDebugUnitTest assembleDebug
```

APK: `research/app-observer/android/app/build/outputs/apk/debug/app-debug.apk`  
Package: `com.ysfclc.appobserver.debug`

## Phone runbook

1. Install App Observer Lab.
2. Grant Usage Access, Notification Access and (optionally) the explicitly described read-only Accessibility service.
3. Return to the app and refresh.
4. Open Uber Driver, Bolt Driver, Waze, Google Maps and WhatsApp normally. Use them as usual for a while; no special interaction is needed.
5. Return and export the JSON report. Review it before sharing it back.

The report contains app/version metadata and aggregate counters only—not notification or screen content.
