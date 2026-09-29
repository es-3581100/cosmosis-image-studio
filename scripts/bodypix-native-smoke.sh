#!/usr/bin/env bash
set -euo pipefail

if [[ "${COSMOSIS_BODYPIX_NATIVE_TESTS:-0}" != "1" ]]; then
  echo "SKIP: set COSMOSIS_BODYPIX_NATIVE_TESTS=1 to permit native BodyPix execution"
  exit 0
fi

: "${COSMOSIS_ORML_BODYPIX_MODEL:?COSMOSIS_ORML_BODYPIX_MODEL must point to the pinned model}"
input="${1:?usage: bodypix-native-smoke.sh input.png [output.png]}"
output="${2:-/tmp/cosmosis-bodypix-mask.png}"
runner="${COSMOSIS_BODYPIX_RUNNER:-orml-runner-bodypix/build/install/orml-runner-bodypix/bin/orml-runner-bodypix}"

test -x "$runner" || { echo "runner not executable: $runner" >&2; exit 2; }
test -f "$input" || { echo "input not found: $input" >&2; exit 2; }

"$runner" --describe | grep -F '"id":"person-body-mask","available":true'
"$runner" --capability person-body-mask --input "$input" --output "$output" --option threshold=0.7 --option internalResolution=0.5

test -s "$output"
printf 'BODYPIX_NATIVE_SMOKE_OK %s\n' "$output"
