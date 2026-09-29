#!/usr/bin/env bash
set -euo pipefail

if [[ "${COSMOSIS_SUPER_RESOLUTION_NATIVE_TESTS:-0}" != "1" ]]; then
  echo "SKIP: set COSMOSIS_SUPER_RESOLUTION_NATIVE_TESTS=1 to permit native FALSR execution"
  exit 0
fi

: "${COSMOSIS_ORML_SUPER_RESOLUTION_MODEL:?COSMOSIS_ORML_SUPER_RESOLUTION_MODEL must point to the pinned FALSR-A model}"
input="${1:?usage: super-resolution-native-smoke.sh input.png [output.png]}"
output="${2:-/tmp/cosmosis-falsr-output.png}"
runner="${COSMOSIS_SUPER_RESOLUTION_RUNNER:-orml-runner-super-resolution/build/install/orml-runner-super-resolution/bin/orml-runner-super-resolution}"

test -x "$runner" || { echo "runner not executable: $runner" >&2; exit 2; }
test -f "$input" || { echo "input not found: $input" >&2; exit 2; }

"$runner" --describe | grep -F '"id":"super-resolution","available":true'
"$runner" --capability super-resolution --input "$input" --output "$output"

test -s "$output"
python3 - "$input" "$output" <<'PY'
import struct,sys

def png_size(path):
    data=open(path,"rb").read(24)
    assert data[:8]==b"\x89PNG\r\n\x1a\n"
    return struct.unpack(">II",data[16:24])

iw,ih=png_size(sys.argv[1])
ow,oh=png_size(sys.argv[2])
assert (ow,oh)==(iw*2,ih*2), ((iw,ih),(ow,oh))
print("SUPER_RESOLUTION_NATIVE_PNG_OK", iw, ih, ow, oh)
PY
printf 'SUPER_RESOLUTION_NATIVE_SMOKE_OK %s\n' "$output"
