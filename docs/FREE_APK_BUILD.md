# Free, private Android APK build

This is the no-Vercel build route for BusyBee. It uses GitHub Codespaces to run the existing Linux build script against the private repository. No repository visibility change and no paid Vercel deployment are required.

## Why this route exists

The hosted Actions run for PR #9 at commit `acfb8d8c3016df2784ba1280c1f74ead7e165bfc` ended before a runner was assigned: runner ID 0, no steps, no logs. No Rust, Gradle, Android test or APK command ran. The failure is therefore not a reported Kotlin/Gradle compilation error.

The Vercel Android sandbox is also unavailable while the Vercel Hobby usage limit is active. Do not use Vercel for Android builds.

## Start a Codespace on the exact BusyBee branch

1. Open PR #9: https://github.com/ysfclc/BusyBee/pull/9
2. Open the PR's source branch, `feat/map-first-route-dock-2026-11-13`, and create a Codespace on that branch. Keep the repository private.
3. Choose a 4-core machine if it is available. The build compiles Rust for Android and then runs the Android Gradle tests; a 2-core machine may be slower or memory-constrained.
4. Wait for the Codespace to finish starting, then open its terminal.

## Keep it at zero cost

GitHub personal Free accounts currently include 120 Codespaces compute hours and 15 GB-month of storage per billing cycle. Compute is counted in core-hours: a 4-core machine uses four core-hours for each hour it is running. That is up to 30 wall-clock hours on a 4-core machine within the 120-hour allowance.

To prevent overage:
- Check the Codespaces usage and budget page before creating the machine.
- Do not add a payment method just for this build. Without a valid payment method, GitHub blocks Codespaces use when the included quota is exhausted rather than charging beyond it.
- If a payment method is already attached, set the Codespaces budget to stop usage at $0 of additional spend.
- Stop the Codespace as soon as the build and download are complete. Delete it afterwards only once any needed files and signing material are safely retained.

This quota applies to personal GitHub accounts. BusyBee is owned by the personal account `ysfclc`, not an organization.

## Build

From the repository root, run:

```bash
bash scripts/vercel-build-apk.sh
```

The script installs/uses the pinned build toolchain (Java 17, Gradle 8.9, Android API 35, Build Tools 35.0.0, NDK 27.2.12479018, Rust stable and cargo-ndk), then runs:

- Rust workspace tests
- Rust Android native-library builds for arm64-v8a, armeabi-v7a and x86_64
- Android `testDebugUnitTest`
- APK assembly and SHA-256 generation

Expected outputs:

- `public/BusyBee.apk`
- `public/BusyBee-download.zip` (contains the exact APK bytes)
- `public/build-info.json` (variant, package ID, version, source commit and SHA-256)

The script exits non-zero if a tool installation, test, native build or APK assembly fails. Do not download an APK unless the script completes successfully.

## Download the APK

In the Codespace file explorer, open `public/`, then use the file menu on `BusyBee-download.zip` and choose **Download**. Extract it on the phone; the APK inside is `BusyBee.apk`.

Alternatively, publish a private GitHub Release asset from the Codespace terminal:

```bash
gh auth status
TAG="codespaces-$(date -u +%Y%m%dT%H%M%S)"
gh release create "$TAG" public/BusyBee-download.zip public/build-info.json \
  --title "BusyBee build $(git rev-parse --short HEAD)" \
  --notes "Private BusyBee APK build. See build-info.json for package, variant, source commit and SHA-256." \
  --prerelease
```

Because the repository is private, the release asset is not public.

## Important: debug APK vs. persistent release APK

The existing build script automatically makes a **debug APK** when the four release-signing environment variables are absent:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

That APK is installable, but its application ID has the `.debug` suffix. It is a separate app from the normal release package and cannot update it.

For an install-over-existing **release APK**, configure one persistent keystore and keep it for every future build. Add the four values above as GitHub Codespaces secrets, grant the BusyBee repository access to those secrets, then stop and recreate/restart the Codespace so the secrets are injected. The existing script will then assemble the normal release package and verify its APK signature. Use the same keystore for GitHub Actions secrets too if hosted builds are resumed later. Never commit the keystore or passwords.

Generate the key once in the Codespace after Java 17 is installed, following `docs/ANDROID_SIGNING.md`. Keep a secure backup of the keystore; losing it prevents future in-place updates signed with that key.

## Current acceptance boundary

A successful Codespaces build will prove compilation, test execution, APK assembly and signature verification for the source commit shown in `public/build-info.json`. It does **not** prove live GPS, MapLibre rendering, turn prompts, Uber/Bolt capture or driving behaviour on the Redmi. Those still require installation and target-phone testing.
