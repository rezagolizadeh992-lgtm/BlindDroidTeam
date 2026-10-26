#!/usr/bin/env bash
set -euo pipefail

ROOT="${HOME}/.local/android-build"
SDK="${ROOT}/sdk"
TOOLS="${ROOT}/cmdline-tools"
GRADLE_VERSION="8.9"
mkdir -p "$ROOT" "$SDK" "$TOOLS"

if [[ ! -x "$ROOT/gradle-${GRADLE_VERSION}/bin/gradle" ]]; then
  curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ROOT/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q -o "$ROOT/gradle-${GRADLE_VERSION}-bin.zip" -d "$ROOT"
fi

if [[ ! -x "$TOOLS/latest/bin/sdkmanager" ]]; then
  mkdir -p "$TOOLS/latest"
  curl -fL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$ROOT/commandlinetools.zip"
  unzip -q -o "$ROOT/commandlinetools.zip" -d "$ROOT/cmdline-tools-extract"
  rm -rf "$TOOLS/latest"
  mv "$ROOT/cmdline-tools-extract/cmdline-tools" "$TOOLS/latest"
  rm -rf "$ROOT/cmdline-tools-extract"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$TOOLS/latest/bin:$SDK/platform-tools:$PATH"

set +o pipefail
yes | "$TOOLS/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null
set -o pipefail
"$TOOLS/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

cat > "$ROOT/env.sh" <<EOF
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$TOOLS/latest/bin:$SDK/platform-tools:$ROOT/gradle-${GRADLE_VERSION}/bin:\$PATH"
EOF

echo "Build tools ready: SDK=$SDK Gradle=$GRADLE_VERSION"
