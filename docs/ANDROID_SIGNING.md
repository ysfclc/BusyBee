# Stable Android release signing

BusyBee's production Android application ID is:

`com.ysfclc.busybee.drivercommandcentre`

Every installable release must use the same persistent keystore. Do not generate a replacement key for future builds: Android will reject updates signed with a different certificate and the user may lose access to existing app data.

## Existing repository Actions secrets

The active release workflow uses exactly these two repository-level Actions secrets:

| Secret | Purpose |
|---|---|
| `BUSYBEE_KEYSTORE_BASE64` | Base64-encoded existing `busybee-release.jks` |
| `BUSYBEE_KEYSTORE_PASSWORD` | Keystore password; also used as the private-key password |

The workflow discovers the single `PrivateKeyEntry` alias from the keystore. It fails if the keystore contains zero or multiple private-key entries. The key alias is not hardcoded and no extra alias/password secrets are required.

Add these under BusyBee → Settings → Secrets and variables → Actions → Repository secrets. Environment-scoped secrets are not read by this workflow.

Never commit the keystore, base64 contents, or passwords. Never paste them into issues, pull requests, source files, or chat.

## Release behavior

- Pull-request validation can produce a debug APK. Its application ID has the `.debug` suffix and it cannot update the installed production app.
- A push to `main` or a manual release build requires the existing keystore and password. If they are unavailable, the workflow fails rather than publishing a debug APK as a release.
- The release build uses the production application ID and the existing signing certificate.
- The workflow verifies the APK signature with `apksigner` before publishing a GitHub prerelease asset.
- The version code is based on the GitHub Actions run number and must increase for every production update.

## Install

Download the signed APK from the successful main-branch workflow/release. Open it on Android and choose Update. Do not uninstall the existing app. With the same package ID and signing certificate, Android accepts it as an update and preserves app-private data.

If Android reports a signature mismatch, stop. Do not uninstall the existing app or clear its data; verify the signing certificate before proceeding.
