# Vercel APK build

This project builds the real Android application from this repository. Vercel is the build host and static APK delivery surface; the app is still compiled by the Android Gradle Plugin and the Rust domain core is compiled for Android by `cargo-ndk`.

## Vercel project

- Project: `busybee-apk`
- Team: `Yusuf's projects` (Hobby)
- Git source: `ysfclc/BusyBee`
- Root directory: repository root
- Build command: `bash scripts/vercel-build-apk.sh`
- Install command: `true`
- Output directory: `public`
- Framework: Other / no framework

The project is separate from the app's production backend or any other Vercel project. Its preview deployments are protected by the team's Vercel authentication setting.

## Build steps

The build script installs and pins the project toolchain versions already used by the CI workflow:

- Java 17 (uses an existing Java 17 runtime or downloads Temurin 17)
- Android command-line tools revision 11076708
- Android platform 35 and build-tools 35.0.0
- Android NDK 27.2.12479018
- Rust stable with `aarch64-linux-android`, `armv7-linux-androideabi` and `x86_64-linux-android`
- `cargo-ndk`
- Gradle 8.9

It then runs Rust workspace tests, builds the three Android Rust libraries, runs Android unit tests (including Robolectric), and assembles an APK. A successful Vercel build copies the APK to `public/BusyBee.apk` and writes `public/build-info.json` with commit, branch, version, package, build timestamp, size and SHA-256.

## Debug versus release

If all four persistent signing variables are absent, the pipeline produces a **debug-only** APK with application ID `com.ysfclc.busybee.drivercommandcentre.debug`. This can be installed alongside a release package but cannot update it.

For an install-over-existing release APK, configure all four Vercel project environment variables with the same persistent key already used for BusyBee releases:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never commit the keystore or put credentials in repository files. The build fails rather than silently falling back if only some signing variables are configured. When all four are configured, the script assembles the release variant and verifies it with `apksigner`.

## Download

After a successful deployment, open the protected Vercel deployment URL and use **Download BusyBee APK**. The page reads the generated build metadata. The direct APK path is `/BusyBee.apk`.

## Limits and verification

- A Vercel build is a full Android toolchain install, not a lightweight web build. The first build may hit Hobby build-time, memory, disk or network limits. The deployment log is the source of truth; no APK is considered ready until Vercel reports the deployment as READY and the APK and metadata are present.
- The current default is debug-only until persistent signing is configured. Do not generate a replacement key to bypass that gate.
- Build output is tied to the exact Git commit shown in `build-info.json`.
- The script does not upload driver data, secrets or source exports.
