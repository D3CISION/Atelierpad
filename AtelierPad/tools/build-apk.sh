#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=8.11.1
PARALLEL="${ATELIER_PARALLEL:-true}"
[[ "$PARALLEL" == true || "$PARALLEL" == false ]] || { echo "ATELIER_PARALLEL must be true or false" >&2; exit 1; }
BASE="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/atelier-bootstrap"
GRADLE="$CACHE/gradle-$VERSION/bin/gradle"
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then export ANDROID_HOME="$HOME/Library/Android/sdk"
  else echo 'Install Android SDK 35 + Build Tools 35.0.0 in Android Studio, then set ANDROID_HOME.' >&2; exit 1; fi
fi
command -v java >/dev/null || { echo 'Install JDK 17 or 21 and set JAVA_HOME.' >&2; exit 1; }
if [ ! -x "$GRADLE" ]; then
  command -v curl >/dev/null; command -v unzip >/dev/null
  mkdir -p "$CACHE"
  TEMP="$(mktemp -d "$CACHE/download.XXXXXX")"
  trap 'rm -rf "$TEMP"' EXIT
  curl --fail --location --proto '=https' --tlsv1.2 "$BASE" -o "$TEMP/gradle.zip"
  curl --fail --location --proto '=https' --tlsv1.2 "$BASE.sha256" -o "$TEMP/expected.sha256"
  EXPECTED="$(tr -d '\r\n ' < "$TEMP/expected.sha256")"
  [[ "$EXPECTED" =~ ^[a-fA-F0-9]{64}$ ]] || { echo 'Invalid upstream checksum.' >&2; exit 1; }
  if command -v sha256sum >/dev/null; then ACTUAL="$(sha256sum "$TEMP/gradle.zip" | cut -d ' ' -f1)"
  else ACTUAL="$(shasum -a 256 "$TEMP/gradle.zip" | cut -d ' ' -f1)"; fi
  [ "$ACTUAL" = "$EXPECTED" ] || { echo 'Gradle checksum mismatch. Aborting.' >&2; exit 1; }
  unzip -q "$TEMP/gradle.zip" -d "$CACHE"
fi
cd "$ROOT"
"$GRADLE" --no-daemon wrapper --gradle-version "$VERSION" --distribution-type bin
"$GRADLE" --no-daemon -PatelierSideBySide="$PARALLEL" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$ROOT"
printf 'Side-by-side install: %s\n' "$PARALLEL"
printf 'Android instrumented tests are compiled, but not run by this script.\n'
