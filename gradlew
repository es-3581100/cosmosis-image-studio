#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
cat >&2 <<'MSG'
Gradle is not installed and this source bundle intentionally does not vendor a binary wrapper JAR.
Install Gradle 9.x or generate the wrapper once with: gradle wrapper --gradle-version 9.0.0
Then run: ./gradlew run
MSG
exit 127
