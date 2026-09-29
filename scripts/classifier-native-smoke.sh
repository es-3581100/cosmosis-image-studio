#!/usr/bin/env bash
set -euo pipefail

if [[ "${COSMOSIS_CLASSIFIER_NATIVE_TESTS:-0}" != "1" ]]; then
  echo "SKIP: set COSMOSIS_CLASSIFIER_NATIVE_TESTS=1 to permit native classifier execution"
  exit 0
fi

: "${COSMOSIS_ORML_CLASSIFIER_MODEL:?COSMOSIS_ORML_CLASSIFIER_MODEL must point to the pinned classifier model}"
input="${1:?usage: classifier-native-smoke.sh input.png [output.json]}"
output="${2:-/tmp/cosmosis-classifier-embedding.json}"
runner="${COSMOSIS_CLASSIFIER_RUNNER:-orml-runner-classifier/build/install/orml-runner-classifier/bin/orml-runner-classifier}"

test -x "$runner" || { echo "runner not executable: $runner" >&2; exit 2; }
test -f "$input" || { echo "input not found: $input" >&2; exit 2; }

"$runner" --describe | grep -F '"id":"image-embedding","available":true'
"$runner" --capability image-embedding --input "$input" --output "$output" --option topK=5

test -s "$output"
python3 - "$output" <<'PY'
import json,sys
data=json.load(open(sys.argv[1]))
assert data["labelsResolved"] is False
assert data["classification"]["dimension"] > 0
assert len(data["classification"]["top"]) == 5
assert data["embedding"]["dimension"] == len(data["embedding"]["values"])
assert data["embedding"]["dimension"] > 0
print("CLASSIFIER_NATIVE_JSON_OK", data["classification"]["dimension"], data["embedding"]["dimension"])
PY
printf 'CLASSIFIER_NATIVE_SMOKE_OK %s\n' "$output"
