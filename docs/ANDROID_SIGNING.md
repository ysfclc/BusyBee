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

## Troubleshooting: CI falls back to a debug APK

The current Build signed BusyBee APK workflow reads these exact repository Actions secret names:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

If the workflow summary says release signing is incomplete, check that all four are present under BusyBee → Settings → Secrets and variables → Actions → Repository secrets, not Actions variables or a different repository. The current workflow does not declare an Actions environment, so environment-scoped secrets are not loaded into this job. Do not paste keystore contents or passwords into workflow files, issues, commits or chat.

A successful debug signature check does not verify the release certificate. The release gate is only passed when the workflow assembles app-release.apk and apksigner verifies it with the persistent release certificate.


## Release workflow behavior and verification

The release workflow now distinguishes pull-request validation from actual release builds:

- A pull_request run can build and upload a debug validation APK. This APK has the .debug application ID and cannot update the installed release app.
- A push to main or a manual workflow_dispatch requires all four persistent signing inputs. If they are not available, the workflow fails early and does not publish a debug APK as a release.
- The PR workflow runs #481 and #482 passed validation but reported that release-signing secrets were not available to the pull-request build. They therefore verified only the Android Debug certificate, not the persistent release certificate.
- A successful non-PR build must assemble app-release.apk and pass apksigner certificate verification before the release-pipeline acceptance gate is complete.

The current build job does not declare a GitHub Actions Environment. Put the four values in the repository's Actions → Repository secrets scope using the exact names above. Environment-scoped secrets are not injected unless the job references that environment. Never put keystore data or passwords in source files, PR descriptions, issue comments or chat.
