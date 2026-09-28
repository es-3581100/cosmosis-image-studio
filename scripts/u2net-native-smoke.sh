#!/usr/bin/env bash
set -euo pipefail

if [[ "${COSMOSIS_U2NET_NATIVE_TESTS:-0}" != "1" ]]; then
  echo "SKIP: set COSMOSIS_U2NET_NATIVE_TESTS=1 to permit native U2Net execution"
  exit 0
fi

: "${COSMOSIS_ORML_U2NET_MODEL:?COSMOSIS_ORML_U2NET_MODEL must point to the pinned model}"
input="${1:?usage: u2net-native-smoke.sh input.png [output.png]}"
output="${2:-/tmp/cosmosis-u2net-mask.png}"
runner="${COSMOSIS_U2NET_RUNNER:-orml-runner-u2net/build/install/orml-runner-u2net/bin/orml-runner-u2net}"

test -x "$runner" || { echo "runner not executable: $runner" >&2; exit 2; }
test -f "$input" || { echo "input not found: $input" >&2; exit 2; }

"$runner" --describe | grep -F '"id":"smart-subject-mask","available":true'
"$runner" --capability smart-subject-mask --input "$input" --output "$output"

test -s "$output"
printf 'U2NET_NATIVE_SMOKE_OK %s\n' "$output"
