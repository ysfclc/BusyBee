#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_ROOT="${BUSYBEE_TOOL_CACHE:-$HOME/.cache/busybee-vercel}"
ANDROID_HOME="${ANDROID_HOME:-$TOOLS_ROOT/android-sdk}"
ANDROID_SDK_ROOT="$ANDROID_HOME"
GRADLE_VERSION="8.9"
NDK_VERSION="27.2.12479018"
BUILD_TOOLS_VERSION="35.0.0"
PLATFORM_VERSION="35"
CMDLINE_TOOLS_REVISION="11076708"

mkdir -p "$TOOLS_ROOT" "$ROOT_DIR/public"
export ANDROID_HOME ANDROID_SDK_ROOT
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/.local/gradle-$GRADLE_VERSION/bin:$HOME/.cargo/bin:$PATH"

need_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

for command_name in curl unzip tar find awk sed sha256sum python3 readlink wc tr base64 chmod yes; do
  need_command "$command_name"
done

echo "==> Preparing Java 17"
JAVA_MAJOR=""
if command -v java >/dev/null 2>&1; then
  JAVA_MAJOR="$(java -version 2>&1 | awk -F '"' '/version/ { split($2, v, "."); if (v[1] == "1") print v[2]; else print v[1]; exit }')"
fi
if [[ "$JAVA_MAJOR" != "17" ]]; then
  JDK_ARCHIVE="$TOOLS_ROOT/temurin-17.tar.gz"
  JDK_DIR="$TOOLS_ROOT/jdk-17"
  if [[ ! -x "$JDK_DIR/bin/java" ]]; then
    curl --fail --location --retry 3 --retry-delay 2 \
      "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" \
      --output "$JDK_ARCHIVE"
    mkdir -p "$JDK_DIR"
    tar -xzf "$JDK_ARCHIVE" --strip-components=1 -C "$JDK_DIR"
  fi
  export JAVA_HOME="$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
else
  export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
fi
java -version

echo "==> Preparing Android SDK command-line tools"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDK_ARCHIVE="$TOOLS_ROOT/commandlinetools-linux-$CMDLINE_TOOLS_REVISION.zip"
  SDK_TEMP="$TOOLS_ROOT/cmdline-tools-unpack"
  curl --fail --location --retry 3 --retry-delay 2 \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_REVISION}_latest.zip" \
    --output "$SDK_ARCHIVE"
  rm -rf "$SDK_TEMP"
  mkdir -p "$SDK_TEMP" "$ANDROID_HOME/cmdline-tools/latest"
  unzip -q "$SDK_ARCHIVE" -d "$SDK_TEMP"
  cp -R "$SDK_TEMP/cmdline-tools/." "$ANDROID_HOME/cmdline-tools/latest/"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

set +o pipefail
if yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null; then
  LICENSE_STATUS=0
else
  LICENSE_STATUS=$?
fi
set -o pipefail
if [[ "$LICENSE_STATUS" -ne 0 && "$LICENSE_STATUS" -ne 141 ]]; then
  echo "Android SDK license acceptance failed (exit $LICENSE_STATUS)." >&2
  exit "$LICENSE_STATUS"
fi

"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-$PLATFORM_VERSION" \
  "build-tools;$BUILD_TOOLS_VERSION" \
  "ndk;$NDK_VERSION"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

echo "==> Preparing Rust and Android targets"
export CARGO_HOME="${CARGO_HOME:-$HOME/.cargo}"
export RUSTUP_HOME="${RUSTUP_HOME:-$HOME/.rustup}"
export PATH="$CARGO_HOME/bin:$PATH"
if [[ ! -x "$CARGO_HOME/bin/rustup" ]]; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain stable
fi
# shellcheck disable=SC1090
source "$CARGO_HOME/env"
rustup toolchain install stable --profile minimal
rustup target add --toolchain stable \
  aarch64-linux-android \
  armv7-linux-androideabi \
  x86_64-linux-android
if ! command -v cargo-ndk >/dev/null 2>&1; then
  cargo install --locked cargo-ndk
fi

echo "==> Preparing Gradle $GRADLE_VERSION"
GRADLE_HOME="$HOME/.local/gradle-$GRADLE_VERSION"
if [[ ! -x "$GRADLE_HOME/bin/gradle" ]]; then
  GRADLE_ARCHIVE="$TOOLS_ROOT/gradle-$GRADLE_VERSION-bin.zip"
  curl --fail --location --retry 3 --retry-delay 2 \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    --output "$GRADLE_ARCHIVE"
  mkdir -p "$HOME/.local"
  unzip -q -o "$GRADLE_ARCHIVE" -d "$HOME/.local"
fi
export PATH="$GRADLE_HOME/bin:$PATH"
gradle --version

BUILD_NUMBER="${BUSYBEE_BUILD_NUMBER:-${GITHUB_RUN_NUMBER:-$(date +%s)}}"
if [[ ! "$BUILD_NUMBER" =~ ^[0-9]+$ ]] || (( BUILD_NUMBER < 1 || BUILD_NUMBER > 2100000000 )); then
  echo "BUSYBEE_BUILD_NUMBER must be an integer between 1 and 2100000000." >&2
  exit 1
fi
export BUSYBEE_BUILD_NUMBER="$BUILD_NUMBER"

echo "==> Running Rust workspace tests"
cd "$ROOT_DIR"
cargo test --workspace --manifest-path rust/Cargo.toml

echo "==> Building Rust JNI libraries for Android"
cd "$ROOT_DIR/rust"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -o ../android/app/src/main/jniLibs \
  build --release --manifest-path Cargo.toml

echo "==> Running Android unit tests"
cd "$ROOT_DIR/android"
./gradlew --no-daemon testDebugUnitTest

SIGNING_COUNT=0
for variable_name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -n "${!variable_name:-}" ]]; then
    SIGNING_COUNT=$((SIGNING_COUNT + 1))
  fi
done

if [[ "$SIGNING_COUNT" -ne 0 && "$SIGNING_COUNT" -ne 4 ]]; then
  echo "Release signing is partially configured. Set all four ANDROID_* signing variables or none." >&2
  exit 1
fi

if [[ "$SIGNING_COUNT" -eq 4 ]]; then
  echo "==> Preparing persistent release keystore"
  KEYSTORE_FILE="$TOOLS_ROOT/busybee-release.jks"
  printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_FILE"
  chmod 600 "$KEYSTORE_FILE"
  export ANDROID_KEYSTORE_FILE="$KEYSTORE_FILE"
  ./gradlew --no-daemon assembleRelease
  APK_SOURCE="$ROOT_DIR/android/app/build/outputs/apk/release/app-release.apk"
  BUILD_VARIANT="release"
  PACKAGE_NAME="com.ysfclc.busybee.drivercommandcentre"
  "$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/apksigner" verify --verbose --print-certs "$APK_SOURCE"
else
  echo "==> No release keystore configured; assembling a debug-only APK"
  ./gradlew --no-daemon assembleDebug
  APK_SOURCE="$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
  BUILD_VARIANT="debug"
  PACKAGE_NAME="com.ysfclc.busybee.drivercommandcentre.debug"
fi

test -s "$APK_SOURCE"
APK_DESTINATION="$ROOT_DIR/public/BusyBee.apk"
cp "$APK_SOURCE" "$APK_DESTINATION"
APK_SHA256="$(sha256sum "$APK_DESTINATION" | awk '{print $1}')"
APK_SIZE="$(wc -c < "$APK_DESTINATION" | tr -d ' ')"
BUILD_TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
COMMIT_SHA="${VERCEL_GIT_COMMIT_SHA:-${GITHUB_SHA:-unknown}}"
COMMIT_REF="${VERCEL_GIT_COMMIT_REF:-${GITHUB_REF_NAME:-unknown}}"
VERSION_NAME="0.1.$BUILD_NUMBER"
export BUSYBEE_BUILD_VARIANT="$BUILD_VARIANT" BUSYBEE_PACKAGE_NAME="$PACKAGE_NAME" BUSYBEE_VERSION_NAME="$VERSION_NAME" BUSYBEE_COMMIT_SHA="$COMMIT_SHA" BUSYBEE_COMMIT_REF="$COMMIT_REF" BUSYBEE_BUILD_TIMESTAMP="$BUILD_TIMESTAMP" BUSYBEE_APK_SHA256="$APK_SHA256" BUSYBEE_APK_SIZE="$APK_SIZE"

python3 - "$ROOT_DIR/public/build-info.json" <<'PY'
import json
import os
import sys

payload = {
    "product": "BusyBee Driver Command Centre",
    "variant": os.environ["BUSYBEE_BUILD_VARIANT"],
    "packageName": os.environ["BUSYBEE_PACKAGE_NAME"],
    "versionCode": int(os.environ["BUSYBEE_BUILD_NUMBER"]),
    "versionName": os.environ["BUSYBEE_VERSION_NAME"],
    "commit": os.environ["BUSYBEE_COMMIT_SHA"],
    "branch": os.environ["BUSYBEE_COMMIT_REF"],
    "deploymentId": os.environ.get("VERCEL_DEPLOYMENT_ID", "local-or-ci"),
    "builtAt": os.environ["BUSYBEE_BUILD_TIMESTAMP"],
    "apk": {
        "file": "BusyBee.apk",
        "sha256": os.environ["BUSYBEE_APK_SHA256"],
        "sizeBytes": int(os.environ["BUSYBEE_APK_SIZE"]),
    },
}
with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump(payload, output, indent=2)
    output.write("\n")
PY
