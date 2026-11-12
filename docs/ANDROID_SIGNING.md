# Stable Android release signing

The fixed Android application ID is com.ysfclc.busybee.drivercommandcentre.

The release workflow refuses to create a release APK until a persistent signing key is configured. A dedicated key package has been prepared for this project. Use the same key for every release; a random key on each CI run would make Android reject upgrades and force uninstall/reinstall.

## Generate the signing key once

Run this on a trusted computer with Java keytool installed. Keep the resulting file and passwords backed up securely. Do not regenerate it for future releases.

    keytool -genkeypair -v -keystore busybee-release.jks -alias busybee -keyalg RSA -keysize 4096 -validity 10000

Choose and securely store the keystore password, key password and alias. The example alias is busybee.

## Encode the keystore

Linux:

    base64 -w 0 busybee-release.jks

macOS:

    base64 < busybee-release.jks | tr -d '\n'

Copy the output directly into a GitHub Actions secret. Never commit the keystore or paste passwords into source files.

## Add four repository Actions secrets

In BusyBee: Settings → Secrets and variables → Actions → New repository secret.

| Secret | Value |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Base64 output from the previous step |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | busybee (or the alias you chose) |
| ANDROID_KEY_PASSWORD | Private-key password |

These names are shared with the existing phase importer.

## Build and install updates

Push a change under rust/ or android/, or run Actions → Build signed BusyBee APK → Run workflow.

The workflow tests Rust and Android, builds a release APK, verifies its signature and uploads the APK as a 30-day artifact.

Download the artifact ZIP from the successful workflow run, extract app-release.apk and open it on Android. Android installs it as an update when package ID and signing certificate match. App data is retained.

Do not lose the keystore. Every future update must be signed with the same key. If it is lost, Android will not accept an in-place update signed with a replacement key.


## Vercel build

The separate `busybee-apk` Vercel project supports the same persistent key. Add the same four values as Vercel project environment variables using the names `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. Do not create a new key for Vercel. If all four variables are absent, Vercel produces a debug-only APK; if only some are set, the build fails. See `docs/VERCEL_APK_BUILD.md`.
