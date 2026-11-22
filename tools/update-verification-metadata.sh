#!/bin/bash
# Refreshes gradle/verification-metadata.xml the way CI sees it: with an EMPTY Gradle cache, so every
# file a clean machine downloads (parent POMs, BOMs, .module files) gets a checksum. Using your normal
# ~/.gradle cache misses files and GitHub builds then fail with "Dependency verification failed".
# Usage: tools/update-verification-metadata.sh   (then review `git diff gradle/verification-metadata.xml`)
set -euo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Reuse the already-downloaded JDKs instead of downloading them again.
paths=$(ls -d "$HOME"/.gradle/jdks/*/*/Contents/Home "$HOME"/.gradle/jdks/*/* 2>/dev/null | grep -E 'Home$|jdk-' | paste -sd, - || true)
printf "org.gradle.java.installations.paths=%s\norg.gradle.java.installations.auto-download=false\n" "$paths" > "$TMP/gradle.properties"
export GRADLE_USER_HOME="$TMP"
./gradlew --no-daemon --write-verification-metadata sha256 \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest help
./gradlew --no-daemon --write-verification-metadata sha256 :app:lintDebug :app:checkReleaseSize :app:dependencies buildEnvironment
echo "Done. Linux-only entries (aapt2 linux jar, CI plugin POMs) are kept from the existing file."
