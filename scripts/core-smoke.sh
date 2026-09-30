#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
FILES=$(find src/main/kotlin/studio/cosmosis -name '*.kt' ! -path '*/ui/*' ! -name 'App.kt' ! -name 'JobEngine.kt' ! -name 'LiveSmoke.kt' ! -name 'AcceptanceSmoke.kt' ! -name 'ProviderContractSmoke.kt' | tr '\n' ' ')
kotlinc $FILES scripts/CoreSmoke.kt -include-runtime -d /tmp/cosmosis-core-smoke.jar
java -Djava.awt.headless=true -jar /tmp/cosmosis-core-smoke.jar
