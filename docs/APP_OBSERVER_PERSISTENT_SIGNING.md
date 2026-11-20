# App Observer — Persistent Release Signing

## Permanent Android identity

The first stable release is configured as:

- Application ID: com.ysfclc.appobserver.capture
- Version: 0.4.0-visual-rules (versionCode 7)
- Signing: one dedicated persistent RSA release key
- Certificate SHA-256: 6c268b04e8deef08ffd000a22cbdf21ec4ab47a816838a59e6fa71ce18bcec6a

Do not change the application ID or replace this signing key after the first stable release is installed. Future release APKs must use the same key and increment versionCode. Debug builds keep a separate .generic.viewer.diagnostics.debug package and are never distributed as release updates.

The earlier 0.3.x viewer APKs used ephemeral GitHub runner debug certificates and different debug package IDs. Android does not permit a differently signed APK to update those packages. The stable release is therefore a one-time side-by-side migration; do not uninstall the earlier apps during migration.

## First stable release produced

The initial stable APK was built from App Observer commit `fb697c578272b1d84914e544ede25246a32ade80` and signed offline with this exact keystore using Android 35 `apksigner`:

- Package: `com.ysfclc.appobserver.capture`
- Version: `0.4.0-visual-rules` (versionCode 7)
- Schemes verified: v2 and v3 (the APK minimum SDK is API 26; both schemes were confirmed by Android `apksigner` verification)
- APK SHA-256: `b0dc5ed37ff4d4241cff2df648c7564d035c73b92fcbc2ad97d3ac15b0a736b3`

The CI workflow still publishes an unsigned, ZIP-aligned release candidate when the repository secrets are absent. The installable first stable APK was signed with the local persistent JKS; no ephemeral debug key was used. Future releases should be signed in CI after the four repository secrets are configured.

## Multimodal learning release 0.5.0

The machine-first screen-understanding update was built from CI run [#265](https://github.com/ysfclc/BusyBee/actions/runs/44806697182). CI passed unit tests, debug/release compilation, and the stable package/version and ZIP-alignment checks. The CI artifact was signed locally with the same persistent JKS and Android 35 `apksigner`.

- Package: `com.ysfclc.appobserver.capture`
- Version: `0.5.0-multimodal` (versionCode 8)
- Signature schemes verified: v2 and v3 (v1 is not present)
- Certificate SHA-256: `6c268b04e8deef08ffd000a22cbdf21ec4ab47a816838a59e6fa71ce18bcec6a`
- APK SHA-256: `38860db1ea4a50f9255770634d43d95c0633e717cb370f0d22505e8a432a02d8`

The signing handoff and keystore remain private and are not committed. GitHub Actions signing secrets have not been configured; future automated releases still need those four repository secrets, or the candidate must be signed offline with the same JKS.

## One-time repository secrets

A keystore was generated outside the public repository. It must remain private and must never be committed.

In GitHub, open ysfclc/BusyBee → Settings → Secrets and variables → Actions and create these repository secrets using the supplied private secrets handoff file. This is a one-time setup; never replace the key when updating the app:

- APP_OBSERVER_KEYSTORE_BASE64
- APP_OBSERVER_KEYSTORE_PASSWORD
- APP_OBSERVER_KEY_ALIAS
- APP_OBSERVER_KEY_PASSWORD

The workflow decodes the keystore only into the runner's temporary directory, builds the release variant, verifies the exact certificate fingerprint and package identity, and uploads the signed release APK. The workflow will still run source tests and debug compilation without these secrets, but it deliberately will not upload an installable APK.

## Backup and loss prevention

Keep two secure backups of the JKS keystore and password. Anyone who can access the private key can sign an APK that Android treats as an update to this app. Do not upload the keystore or password to the public repository, an issue, a pull request, or a public release asset. If the key is lost, the installed package cannot receive normal signed updates with a replacement key; the user would need a different package identity and another migration.

## Existing evidence and app migration

Before moving from an earlier debug package, export its latest evidence ZIP from that app. Install the first stable release alongside the older app, then use IMPORT PREVIOUS APP EVIDENCE ZIP in the stable release. This imports old JSONL session evidence and screenshots and merges valid semantic rules. Keep the old app and exported ZIP until the import has been inspected.

After the first stable release is installed, future signed releases with the same package and key install as updates; they preserve app-private session evidence, screenshots and meaning rules without uninstalling the app.
